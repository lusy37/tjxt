package com.tianji.promotion.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.autoconfigure.redisson.annotations.Lock;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.*;
import com.tianji.promotion.constants.ObtainType;
import com.tianji.promotion.constants.ScopeType;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.dto.CouponScopeDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.tianji.promotion.strategy.scope.Scope;
import com.tianji.promotion.strategy.scope.ScopeNameHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.tianji.promotion.constants.CouponStatus.*;
import static com.tianji.promotion.constants.PromotionErrorInfo.COUPON_ISSUE_EXPIRED;
import static com.tianji.promotion.constants.PromotionErrorInfo.COUPON_NOT_EXISTS;

/**
 * <p>
 * 优惠券的规则信息 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-09-06
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {

    private final ICouponScopeService scopeService;
    private final IUserCouponService userCouponService;
    private final IExchangeCodeService codeService;
    private final Map<String, ScopeNameHandler> scopeNameHandlers;
    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitMqHelper rabbitMqHelper;

    private final static String COUPON_CACHE_KEY_PREFIX = "promotion:cache:coupon:";
    private final static String COUPON_ORDER_KEY_PREFIX = "promotion:order:coupon:";

    @Override
    @Transactional
    public void saveCoupon(CouponFormDTO couponDTO) {
        // 1.判断优惠券推广方式，如果是兑换码则需要验证兑换码数量
        checkObtainWay(couponDTO);

        // 2.数据转换
        Coupon coupon = BeanUtils.copyBean(couponDTO, Coupon.class);
        // 3.初始化状态
        coupon.setStatus(DRAFT.getValue());
        save(coupon);

        // 4.判断优惠券范围
        if (!coupon.getSpecific()) {
            // 说明是没有限定使用范围，直接结束
            return;
        }
        // 5.需要设置优惠券范围，先判断是否有值
        if (CollUtils.isEmpty(couponDTO.getScopes())) {
            throw new BadRequestException("优惠券范围不能为空");
        }
        // 6.保存优惠券范围信息
        Long couponId = coupon.getId();
        List<CouponScope> couponScopeList = couponDTO.getScopes().stream()
                .flatMap(d -> d.getScopeIds().stream()
                        .map(s -> new CouponScope(d.getScopeType().getValue(), couponId, s)))
                .collect(Collectors.toList());
        scopeService.saveBatch(couponScopeList);
    }

    private void checkObtainWay(CouponFormDTO couponDTO) {
        Integer obtainWay = couponDTO.getObtainWay();
        if (obtainWay != null && ObtainType.PUBLIC.equalsValue(obtainWay)) {
            if (couponDTO.getTotalNum() < 1 || couponDTO.getTotalNum() >= 5000) {
                // 兑换码数量不正确
                throw new BadRequestException("优惠券数量不正确");
            }
        }
    }

    @Override
    public void deleteById(Long id) {
        boolean success = remove(new LambdaQueryWrapper<Coupon>()
                .eq(Coupon::getId, id)
                .eq(Coupon::getStatus, DRAFT)
        );
        if (!success) {
            throw new BadRequestException("优惠券不存在或者优惠券正在使用中");
        }
    }

    @Override
    @Transactional
    public void updateCoupon(CouponFormDTO couponDTO) {
        // 1.校验优惠券兑换码数量
        checkObtainWay(couponDTO);
        // 2.查询旧数据
        Coupon old = getById(couponDTO.getId());
        if (old == null) {
            throw new BadRequestException(COUPON_NOT_EXISTS);
        }
        // 3.更新优惠券
        Coupon coupon = BeanUtils.copyBean(couponDTO, Coupon.class);
        // 3.1.避免修改状态
        coupon.setStatus(null);
        // 3.2.更新
        updateById(coupon);

        // 4.判断是否要设定新的优惠券范围
        if (couponDTO.getSpecific() == null || (!old.getSpecific() && !couponDTO.getSpecific())) {
            // 4.1.本次修改没有指定范围，直接结束
            return;
        }
        // 4.2.本次修改指定了范围，判断范围数据是否存在
        if (couponDTO.getSpecific() && CollUtils.isEmpty(couponDTO.getScopes())) {
            // 没有指定新范围，直接结束
            return;
        }

        // 5.删除旧限定范围
        Long couponId = coupon.getId();
        scopeService.removeByCouponId(couponId);

        // 6.添加新范围
        List<CouponScope> couponScopeList = couponDTO.getScopes().stream()
                .flatMap(d -> d.getScopeIds().stream()
                        .map(s -> new CouponScope(d.getScopeType().getValue(), couponId, s)))
                .collect(Collectors.toList());

        scopeService.saveBatch(couponScopeList);
    }

    @Override
    public PageDTO<CouponPageVO> queryCouponPage(CouponQuery query) {
        // 1.分页搜索
        Page<Coupon> page = lambdaQuery()
                .eq(query.getStatus() != null, Coupon::getStatus, query.getStatus())
                .eq(query.getType() != null, Coupon::getDiscountType, query.getType())
                .like(StringUtils.isNotBlank(query.getName()), Coupon::getName, query.getName())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        // 2.数据处理
        List<Coupon> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 2.1.获取优惠券id
        List<Long> couponIds = records.stream().map(Coupon::getId).collect(Collectors.toList());
        // 2.2.统计优惠券已经使用的数量
        Map<Long, Integer> usedTimes = userCouponService.countUsedTimes(couponIds);

        // 3.转换vo
        List<CouponPageVO> list = new ArrayList<>(records.size());
        for (Coupon r : records) {
            CouponPageVO v = BeanUtils.toBean(r, CouponPageVO.class);
            list.add(v);
            v.setRule(r.discount().getRule());
            v.setUsed(usedTimes.get(r.getId()));
        }
        return PageDTO.of(page, list);
    }

    @Override
    public CouponDetailVO queryCouponById(Long id) {
        // 1.查询优惠券
        Coupon coupon = getById(id);
        if (coupon == null) {
            throw new BadRequestException(COUPON_NOT_EXISTS);
        }

        // 2.数据转换
        CouponDetailVO vo = BeanUtils.toBean(coupon, CouponDetailVO.class);

        // 3.查询优惠券关联的使用范围
        Boolean specific = coupon.getSpecific();
        // 3.1.没有限定范围，无需额外查询
        if (!specific) {
            return vo;
        }
        // 3.2.限定使用范围，查询范围
        List<Scope> scopes = scopeService.queryScopeByCouponId(id);
        // 3.3.转换Scope为DTO
        List<CouponScopeDTO> scopeDTOS = scopes.stream()
                .map(this::transferScopeDTO).collect(Collectors.toList());
        vo.setScopes(scopeDTOS);
        return vo;
    }

    @Override
    public void pauseIssue(Long id) {
        // 1.查询旧优惠券
        Coupon coupon = getById(id);
        AssertUtils.isNotNull(coupon, COUPON_NOT_EXISTS);

        // 2.当前券状态必须是未开始或进行中
        Integer status = coupon.getStatus();
        if (!UN_ISSUE.equalsValue(status) && !ISSUING.equalsValue(status)) {
            // 状态错误，直接结束
            return;
        }

        // 3.更新状态
        boolean success = lambdaUpdate()
                .set(Coupon::getStatus, PAUSE.getValue())
                .eq(Coupon::getId, id)
                .in(Coupon::getStatus, UN_ISSUE.getValue(), ISSUING.getValue())
                .update();
        if (success) {
            // 可能是重复更新，结束
            return;
        }

        // 4.删除优惠券缓存
        stringRedisTemplate.delete(COUPON_CACHE_KEY_PREFIX + id);
    }

    @Override
    @Transactional
    public void beginIssue(CouponIssueFormDTO dto) {
        Long id = dto.getId();
        // 1.查询旧优惠券
        Coupon coupon = getById(id);
        AssertUtils.isNotNull(coupon, COUPON_NOT_EXISTS);
        // 2.判断状态，当前券必须是暂停或待发放
        Integer status = coupon.getStatus();
        if (!PAUSE.equalsValue(status) && !DRAFT.equalsValue(status)) {
            // 状态错误，直接结束
            return;
        }
        // 3.修改优惠券信息
        Coupon c = new Coupon();
        c.setId(id);
        // 3.1.时间信息
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime issueBeginTime = dto.getIssueBeginTime();
        // 如果开始时间为空，说明是立即开始，需要修改优惠券状态为发放中
        if (issueBeginTime == null) {
            issueBeginTime = now;
            c.setStatus(ISSUING.getValue());
        }
        c.setIssueBeginTime(issueBeginTime);
        c.setIssueEndTime(dto.getIssueEndTime());
        // 3.2.更新
        updateById(c);

        // 4.建立Redis缓存
        Map<String, Object> map = new HashMap<>();
        map.put("issueBeginTime", DateUtils.toEpochMilli(c.getIssueBeginTime()));
        map.put("issueEndTime", DateUtils.toEpochMilli(c.getIssueEndTime()));
        map.put("totalNum", c.getTotalNum());
        map.put("userLimit", c.getUserLimit());
        stringRedisTemplate.opsForHash().putAll(COUPON_CACHE_KEY_PREFIX + id, map);

        // 5.判断优惠券发放方式是否是手动发放，如果是的话需要生成兑换码
        if (ObtainType.ISSUE.equalsValue(coupon.getObtainWay())) {
            coupon.setIssueEndTime(c.getIssueEndTime());
            codeService.generateExchangeCodeAsync(coupon);
        }
    }

    @Override
    @Lock(formatter = "promotion:lock:coupon:#{couponId}", unlock = true, waitTime = 30, block = true)
    public void snapUpCoupon(Long couponId) {
        // 1.查询优惠券缓存信息
        String key = COUPON_CACHE_KEY_PREFIX + couponId;
        Map<Object, Object> couponObj = stringRedisTemplate.opsForHash().entries(key);
        AssertUtils.isNotEmpty(couponObj, COUPON_NOT_EXISTS);
        Coupon coupon = BeanUtils.mapToBean(couponObj, Coupon.class, false, CopyOptions.create());

        // 2.校验时间，优惠券领取未开始或已经结束抛出异常
        LocalDateTime now = LocalDateTime.now();
        AssertUtils.isTrue(coupon.getIssueBeginTime().isBefore(now) && coupon.getIssueEndTime().isAfter(now),
                COUPON_ISSUE_EXPIRED);

        // 3.校验库存，库存不足则抛出异常
        AssertUtils.isTrue(coupon.getTotalNum() > 0, "优惠券库存不足");

        // 4.校验每人限领数量
        Long userId = UserContext.getUser();
        if (coupon.getUserLimit() != null && coupon.getUserLimit() > 0) {
            // 4.1.确认需要校验每人限领数量
            String orderKey = COUPON_ORDER_KEY_PREFIX + couponId;
            // 4.2.查询当前券已领数量并验证
            Long userOrderNum = stringRedisTemplate.opsForHash().increment(orderKey, userId.toString(), 1);
            AssertUtils.isTrue(userOrderNum <= coupon.getUserLimit(), "超出限领数量");
            // 4.3.未超领取上限，可以领取
        }

        // 5.可以领取，累计优惠券已领取数量
        stringRedisTemplate.opsForHash().increment(key, "totalNum", -1);

        // 6.发送MQ消息，异步修改数据库
        long id = IdWorker.getId();
        UserCoupon uc = new UserCoupon();
        uc.setId(id);
        uc.setUserId(userId);
        uc.setCouponId(couponId);
        rabbitMqHelper.send(MqConstants.Exchange.PROMOTION_EXCHANGE, MqConstants.Key.COUPON_RECEIVE, uc);
    }

    @Override
    @Transactional
    public void snapUpCoupon(UserCoupon userCoupon) {
        Long couponId = userCoupon.getCouponId();
        Long ucId = userCoupon.getId();
        // 1.幂等校验，查看id是否已经存在
        UserCoupon c = userCouponService.getById(ucId);
        if (c != null) {
            // 优惠券存在了，说明是重复业务，结束
            log.error("用户领券重复处理。券：{}，token：{}", couponId, ucId);
            return;
        }

        // 2.查询优惠券
        Coupon coupon = getById(couponId);
        if (coupon == null) {
            // 优惠券不存在，结束
            log.error("用户领取的优惠券不存在：{}", couponId);
            return;
        }

        // 3.校验时间
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getIssueBeginTime().isAfter(now) || coupon.getIssueEndTime().isBefore(now)) {
            // 优惠券领取未开始或已经结束，结束
            log.error("用户领取的优惠券未开始或已经结束：{}", couponId);
            return;
        }
        // 4.校验每人限领数量
        Long userId = userCoupon.getUserId();
        if (coupon.getUserLimit() != null && coupon.getUserLimit() > 0) {
            // 4.1.需要校验每人限领数量，查询当前用户已经领取的券数量
            int userReceiveNum = userCouponService.countUserReceiveNum(couponId, userId);
            if(userReceiveNum >= coupon.getUserLimit()){
                // 4.2.超出上限，结束
                log.error("用户领取的优惠券数量超出限制：券：{}，数量：{}", couponId, userReceiveNum);
                return;
            }
        }
        // 5.修改券库存数量
        baseMapper.minusCouponIssueAmount(coupon.getId());

        // 6.写入用户券
        userCouponService.createUserCouponWithId(coupon, ucId, userId);
    }

    private CouponScopeDTO transferScopeDTO(Scope s) {
        if (s.getType() == ScopeType.ALL) {
            // 没有范围要求
            return CouponScopeDTO.builder().scopeType(ScopeType.ALL).build();
        }
        ScopeNameHandler handler = scopeNameHandlers.get(s.getType().name());
        if (handler == null) {
            throw new BadRequestException("错误的范围类型");
        }
        return CouponScopeDTO.builder()
                .scopeType(s.getType())
                .scopeIds(s.getScopeIds())
                .scopeNames(handler.getNameByIds(s.getScopeIds()))
                .build();
    }
}
