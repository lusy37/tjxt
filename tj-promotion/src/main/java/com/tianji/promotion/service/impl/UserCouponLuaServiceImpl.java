package com.tianji.promotion.service.impl;

import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.NumberUtils;
import com.tianji.promotion.constant.PromotionConstants;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
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
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.CodeUtil;
import com.tianji.promotion.utils.MyLock;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tianji.promotion.constant.PromotionConstants.COUPON_CODE_MAP_KEY;
import static com.tianji.promotion.constant.PromotionConstants.COUPON_RANGE_KEY;

/*
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author lusy
 * @since 2025-03-20
*/

@Service
@RequiredArgsConstructor
public class UserCouponLuaServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {

    private final StringRedisTemplate redisTemplate;
    private final IExchangeCodeService codeService;
    private final RabbitMqHelper rabbitMqHelper;
    private final CouponMapper couponMapper;
    private static final RedisScript<Long> RECEIVE_COUPON_SCRIPT;
    private static final RedisScript<String> EXCHANGE_COUPON_SCRIPT;

    static {
        RECEIVE_COUPON_SCRIPT = RedisScript.of(new ClassPathResource("lua/receive_coupon.lua"), Long.class);
        EXCHANGE_COUPON_SCRIPT = RedisScript.of(new ClassPathResource("lua/exchange_coupon.lua"), String.class);
    }



    @Override
    public void receiveCoupon(Long couponId) {
        // 准备参数
        String key1 = PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId;
        String key2 = PromotionConstants.USER_COUPON_CACHE_KEY_PREFIX + couponId;
        Long userId = UserContext.getUser();
        Long r = redisTemplate.execute(RECEIVE_COUPON_SCRIPT, List.of(key1, key2), userId.toString());
        int result = NumberUtils.null2Zero(r).intValue();
        if (result != 0) {
            // 结果大于0, 说明领取失败
            throw new BizIllegalException(PromotionConstants.RECEIVE_COUPON_ERROR_MSG[result - 1]);
        }
        // 发送MQ消息
        UserCouponDTO userCouponDTO = new UserCouponDTO();
        userCouponDTO.setUserId(userId);
        userCouponDTO.setCouponId(couponId);
        rabbitMqHelper.send(MqConstants.Exchange.PROMOTION_EXCHANGE, MqConstants.Key.COUPON_RECEIVE_KEY, userCouponDTO);
    }

    @Override
    public void exchangeCoupon(String code) {
        // 1.校验并解析兑换码
        long serialNum = CodeUtil.parseCode(code);
        // 2.执行LUA脚本
        Long userId = UserContext.getUser();
        String result = redisTemplate.execute(
                EXCHANGE_COUPON_SCRIPT,
                List.of(COUPON_CODE_MAP_KEY, COUPON_RANGE_KEY),
                String.valueOf(serialNum), String.valueOf(serialNum + 5000), userId.toString());
        long r = NumberUtils.parseLong(result);
        if (r < 10) {
            // 异常结果应该是在1~5之间
            throw new BizIllegalException(PromotionConstants.EXCHANGE_COUPON_ERROR_MSG[(int) (r - 1)]);
        }
        // 3.发送MQ消息通知
        UserCouponDTO uc = new UserCouponDTO();
        uc.setUserId(userId);
        uc.setCouponId(r);
        uc.setSerialNum(serialNum);
        rabbitMqHelper.send(MqConstants.Exchange.PROMOTION_EXCHANGE, MqConstants.Key.COUPON_RECEIVE_KEY, uc);
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
    @Transactional
    @Override
    public void checkAndCreateUserCouponByMq(UserCouponDTO uc) {

        // 1.查询优惠券
        Coupon coupon = couponMapper.selectById(uc.getCouponId());
        if (coupon == null) {
            throw new BizIllegalException("优惠券不存在！");
        }
        // 2.更新优惠券的已经发放的数量 + 1
        int r = couponMapper.incrIssueNum(coupon.getId());
        if (r == 0) {
            throw new BizIllegalException("优惠券库存不足！");
        }
        // 3.新增一个用户券
        saveUserCoupon(coupon, uc.getUserId());

        // 4.更新兑换码状态
        if (uc.getSerialNum() != null) {
            codeService.lambdaUpdate()
                    .set(ExchangeCode::getUserId, uc.getUserId())
                    .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                    .eq(ExchangeCode::getId, uc.getSerialNum())
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
