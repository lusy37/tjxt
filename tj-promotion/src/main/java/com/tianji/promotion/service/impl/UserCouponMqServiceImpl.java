package com.tianji.promotion.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.constant.PromotionConstants;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.pojo.Coupon;
import com.tianji.promotion.domain.pojo.ExchangeCode;
import com.tianji.promotion.domain.pojo.UserCoupon;
import com.tianji.promotion.domain.query.UserCouponQuery;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.tianji.promotion.utils.CodeUtil;
import com.tianji.promotion.utils.MyLock;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author lusy
 * @since 2025-03-23
 */
@Service
@RequiredArgsConstructor
public class UserCouponMqServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {

    private final CouponMapper couponMapper;
    private final IExchangeCodeService codeService;
    private final StringRedisTemplate redisTemplate;
    private final RabbitMqHelper rabbitMqHelper;
    private final RedissonClient redissonClient;
    @Override
    @MyLock(name = "lock:coupon:uid:#{T(com.tianji.common.utils.UserContext).getUser()}")
    public void receiveCoupon(Long couponId) {
        // 校验 couponId
        if (couponId == null) {
            throw new BadRequestException("优惠券id不能为空");
        }
        // 获取当前用户 id
        Long userId = UserContext.getUser();
        // 获取优惠券信息
        Coupon coupon = queryCouponByCache(couponId);
        // 校验优惠券是否存在
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在");
        }
        // 校验是否处于发放状态
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getIssueBeginTime()) || now.isAfter(coupon.getIssueEndTime())) {
            throw new BadRequestException("优惠券发放已经结束或尚未开始");
        }
        // 校验库存是否充足
        if (coupon.getTotalNum() <= 0) {
            throw new BadRequestException("优惠券库存不足");
        }
        // 统计已领取的数量
        String key = PromotionConstants.USER_COUPON_CACHE_KEY_PREFIX + couponId;
        Long count = redisTemplate.opsForHash().increment(key, userId.toString(), 1);
        // 校验限领数量
        if(count > coupon.getUserLimit()){
            throw new BadRequestException("超出领取数量");
        }
        // 扣减库存
        try {
            redisTemplate.opsForHash().increment(
                    PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId, "totalNum", -1);
        } catch (Exception e) {
            // 扣减库存失败，回滚已领取数量
            redisTemplate.opsForHash().increment(key, userId.toString(), -1);
            throw new RuntimeException(e);
        }
        /*        // 创建锁对象
                String key = "lock:coupon:uid" + userId;
                RLock lock = redissonClient.getLock(key);
                // 尝试获取锁
                boolean isLock = lock.tryLock();
                if (!isLock) {
                    throw new BizIllegalException("请求太频繁");
                }
                try {
                    IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
                    userCouponService.checkAndCreateUserCoupon(userId, coupon, null);
                    // checkAndCreateUserCoupon(userId, coupon, null);
                } finally {
                    lock.unlock();
                }*/
        // 发送MQ消息
        UserCouponDTO userCouponDTO = new UserCouponDTO();
        userCouponDTO.setUserId(userId);
        userCouponDTO.setCouponId(couponId);
        rabbitMqHelper.send(MqConstants.Exchange.PROMOTION_EXCHANGE, MqConstants.Key.COUPON_RECEIVE_KEY, userCouponDTO);
    }

    private Coupon queryCouponByCache(Long couponId) {
        // 封装 Redis 的 Key
        String key = PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId;
        // 从 Redis 中查询优惠券信息
        Map<Object, Object> objMap = redisTemplate.opsForHash().entries(key);
        if (CollUtils.isEmpty(objMap)) {
            return null;
        }
        // 数据反序列化
        return BeanUtil.mapToBean(objMap, Coupon.class, false, CopyOptions.create());
    }

    /**
     * 兑换优惠券
     * @param code 兑换码
     */
    @Override
    @MyLock(name = "lock:coupon:uid:#{code}")
    public void exchangeCoupon(String code) {
        // 解析兑换码, 得到兑换码对应的 id
        long serialNum = CodeUtil.parseCode(code);
        // 判断兑换码是否已经使用过
        boolean exchanged = codeService.updateExchangeMark(serialNum, true);
        if (exchanged) {
            throw new BadRequestException("兑换码已经使用过");
        }
        try {
            // 查询兑换码对应的优惠券 id
            Long couponId = codeService.exchangeTargetId(serialNum);
            if (couponId == null) {
                throw new BadRequestException("兑换码不存在");
            }
            // 从Redis中获取优惠券信息
            Coupon coupon = queryCouponByCache(couponId);
            // 判断兑换码是否已经过期
            LocalDateTime now = LocalDateTime.now();
            if (now.isAfter(coupon.getIssueEndTime()) || now.isBefore(coupon.getIssueBeginTime())) {
                throw new BizIllegalException("优惠券活动未开始或已经结束");
            }
            // 判断是否超出限领数量
            /*            // 创建锁对象
                        String key = "lock:coupon:uid" + userId;
                        RLock lock = redissonClient.getLock(key);
                        // 尝试获取锁
                        boolean isLock = lock.tryLock();
                        if (!isLock) {
                            throw new BizIllegalException("请求太频繁");
                        }
                        try {
                            IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
                            userCouponService.checkAndCreateUserCoupon(userId, coupon, serialNum);
                            // checkAndCreateUserCoupon(userId, coupon, null);
                        } finally {
                            lock.unlock();
                        }*/
            Long userId = UserContext.getUser();
            String key = PromotionConstants.USER_COUPON_CACHE_KEY_PREFIX + couponId;
            Long count = redisTemplate.opsForHash().increment(key, userId.toString(), 1);
            // 校验限领数量
            if(count > coupon.getUserLimit()){
                throw new BadRequestException("超出领取数量");
            }
            // 发送MQ消息
            UserCouponDTO userCouponDTO = new UserCouponDTO();
            userCouponDTO.setUserId(userId);
            userCouponDTO.setCouponId(couponId);
            userCouponDTO.setSerialNum(serialNum);
            rabbitMqHelper.send(MqConstants.Exchange.PROMOTION_EXCHANGE, MqConstants.Key.COUPON_RECEIVE_KEY, userCouponDTO);
        } catch (Exception e) {
            // 回滚 redis 操作
            codeService.updateExchangeMark(serialNum, false);
            throw e;
        }
    }


    @Transactional
    @MyLock(name = "lock:coupon:uid:#{userId}")
    public void checkAndCreateUserCoupon(Long userId, Coupon coupon, Long serialNum) {
        // 校验是否达到限领数量
        Integer count = lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, coupon.getId())
                .count();
        if (count >= coupon.getUserLimit()) {
            throw new BadRequestException("优惠券已经达到限领数量");
        }

        // 5.更新优惠券的已经发放的数量 + 1
        int r = couponMapper.incrIssueNum(coupon.getId());
        if (r != 1) {
            throw new BadRequestException("优惠券发放失败");
        }
        // 6.新增一个用户券
        saveUserCoupon(coupon, userId);

        // 更新兑换码状态
        if (serialNum != null) {
            codeService.lambdaUpdate()
                    .set(ExchangeCode::getUserId, userId)
                    .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                    .eq(ExchangeCode::getId, serialNum)
                    .update();
        }
    }

    @Override
    public PageDTO<CouponPageVO> queryMyCoupons(UserCouponQuery query) {

        Page<UserCoupon> page = lambdaQuery()
                .eq(UserCoupon::getUserId, UserContext.getUser())
                .eq(UserCoupon::getStatus, query.getStatus())
                .page(query.toMpPage("term_end_time", true));
        List<UserCoupon> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 转化成VO
        Set<Long> couponIds = records.stream().map(UserCoupon::getCouponId).collect(Collectors.toSet());
        Map<Long, Coupon> couponsMap = null;
        List<Coupon> coupons = couponMapper.selectBatchIds(couponIds);
        if (CollUtils.isNotEmpty(coupons)) {
            couponsMap = coupons.stream().collect(Collectors.toMap(Coupon::getId, c -> c));
        }
        List<CouponPageVO> voList = new ArrayList<>();
        for (UserCoupon record : records) {
            Coupon coupon = couponsMap.get(record.getCouponId());
            CouponPageVO vo = BeanUtil.copyProperties(coupon, CouponPageVO.class);
            voList.add(vo);
        }
        return PageDTO.of(page, voList);
    }

    @Override
    public void checkAndCreateUserCouponByMq(UserCouponDTO uc) {
        Long couponId = uc.getCouponId();
        Long userId = uc.getUserId();
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
             log.error("优惠券不存在");
             return;
        }
        // 更新优惠券的发放数量 + 1
        int r = couponMapper.incrIssueNum(couponId);
        if (r != 1) {
            log.error("优惠券发放失败");
            return;
        }
        // 新增一个用户券
        saveUserCoupon(coupon, userId);

        //更新兑换码状态
        Long serialNum = uc.getSerialNum();
        if (serialNum != null) {
            codeService.lambdaUpdate()
                    .set(ExchangeCode::getUserId, userId)
                    .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                    .eq(ExchangeCode::getId, serialNum)
                    .update();
        }
    }

    private void saveUserCoupon(Coupon coupon, Long userId) {
        // 1.基本信息
        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setCouponId(coupon.getId());
        // 2.有效期信息
        LocalDateTime termBeginTime = coupon.getTermBeginTime();
        LocalDateTime termEndTime = coupon.getTermEndTime();
        if (termBeginTime == null) {
            termBeginTime = LocalDateTime.now();
            termEndTime = termBeginTime.plusDays(coupon.getTermDays());
        }
        uc.setTermBeginTime(termBeginTime);
        uc.setTermEndTime(termEndTime);
        // 3.保存
        save(uc);
    }
}
