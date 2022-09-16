package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.dto.IdAndNumDTO;
import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCouponDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.AssertUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.constants.PromotionErrorInfo;
import com.tianji.promotion.constants.UserCouponStatus;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.UserCouponQuery;
import com.tianji.promotion.domain.vo.UserCouponVO;
import com.tianji.promotion.strategy.discount.Discount;
import com.tianji.promotion.strategy.scope.Scope;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.IUserCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
 * @author 虎哥
 * @since 2022-09-06
 */
@Service
@RequiredArgsConstructor
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {

    private final CouponMapper couponMapper;
    private final ICouponScopeService scopeService;

    @Override
    @SuppressWarnings("all")
    public Map<Long, Integer> countUsedTimes(List<Long> couponIds) {
        // 1.查询条件
        QueryWrapper<UserCoupon> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .in(UserCoupon::getCouponId, couponIds)
                .eq(UserCoupon::getStatus, UserCouponStatus.USED.getValue())
                .groupBy(UserCoupon::getCouponId);
        // 2.统计数量
        List<IdAndNumDTO> list = getBaseMapper().countUsedTimes(wrapper);
        // 3.数据转换
        return IdAndNumDTO.toMap(list);
    }

    @Override
    public PageDTO<UserCouponVO> queryUserCouponPage(UserCouponQuery query) {
        // 1.查询数据
        Page<UserCoupon> page = lambdaQuery()
                .eq(UserCoupon::getStatus, query.getStatus())
                .page(query.toMpPage("term_end_time", true));

        // 2.非空处理
        List<UserCoupon> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        // 3.查询优惠券规则
        Set<Long> couponIds = records.stream().map(UserCoupon::getCouponId).collect(Collectors.toSet());
        List<Coupon> coupons = couponMapper.selectByIds(couponIds);
        if (coupons == null || coupons.size() != couponIds.size()) {
            // 优惠券信息找不到
            throw new BizIllegalException("优惠券数据异常");
        }
        Map<Long, Coupon> couponMap = coupons.stream().collect(Collectors.toMap(Coupon::getId, c -> c));
        // 4.转VO
        return PageDTO.of(page, userCoupon -> {
            Coupon coupon = couponMap.get(userCoupon.getCouponId());
            UserCouponVO v = new UserCouponVO();
            v.setName(coupon.getName());
            v.setDiscountType(coupon.getDiscountType());
            v.setSpecify(coupon.getSpecific());
            v.setDiscountValue(coupon.getDiscountValue());
            v.setRule(coupon.discount().getRule());
            v.setId(userCoupon.getId());
            v.setTermEndTime(userCoupon.getTermEndTime());
            return v;
        });
    }

    @Override
    public List<CouponDiscountDTO> queryAvailableCoupon(List<OrderCourseDTO> courses) {
        // 1.查询出用户的所有可用优惠券
        List<Coupon> coupons = baseMapper.queryMyCoupon(UserContext.getUser());
        if (CollUtils.isEmpty(coupons)) {
            return CollUtils.emptyList();
        }

        List<CouponDiscountDTO> list = new ArrayList<>();
        // 2.过滤优惠券，判断是否可用（范围判断、金额判断）
        for (Coupon coupon : coupons) {
            // 2.1.尝试做优惠券计算
            CouponDiscountDTO dto = tryCalculateDiscount(courses, coupon);
            // 2.2.如果结果为null，说明该券不能使用，跳过
            if (dto == null) continue;
            // 2.3.存入结果
            list.add(dto);
        }
        // 3.返回
        return list;
    }

    private CouponDiscountDTO tryCalculateDiscount(List<OrderCourseDTO> courses, Coupon coupon) {
        // 1.计算优惠券可用课程的总价
        int totalAmount;
        // 2.判断是否指定了优惠券范围
        if (coupon.getSpecific()) {
            // 指定范围的话，查询优惠券范围，过滤课程，计算总价
            List<Scope> scopes = scopeService.queryScopeByCouponId(coupon.getId());
            totalAmount = courses.stream()
                    .filter(c -> scopes.stream().anyMatch(s -> s.canUse(c)))
                    .mapToInt(OrderCourseDTO::getPrice).sum();
        } else {
            // 未指定范围的话，直接计算总价
            totalAmount = courses.stream().mapToInt(OrderCourseDTO::getPrice).sum();
        }
        // 3.判断是否达到优惠券是否可对当前课程打折
        Discount discount = coupon.discount();
        if (!discount.canUse(totalAmount)) {
            return null;
        }
        // 4.转为DTO
        return new CouponDiscountDTO(
                coupon.getId(), discount.getRule(), discount.calculateDiscount(totalAmount));
    }

    @Override
    public CouponDiscountDTO queryDiscountByCouponId(OrderCouponDTO orderCouponDTO) {
        // 1.查询用户优惠券
        Long userCouponId = orderCouponDTO.getUserCouponId();
        Coupon coupon = baseMapper.queryCouponByUserCouponId(userCouponId);
        AssertUtils.isNotNull(coupon, PromotionErrorInfo.INVALID_USER_COUPON);
        // 2.查询优惠券规则
        return tryCalculateDiscount(orderCouponDTO.getCourseList(), coupon);
    }

    @Override
    public int countUserReceiveNum(Long couponId, Long userId) {
        return lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, couponId)
                .count();
    }

    @Override
    public void createUserCouponWithId(Coupon coupon, Long id, Long userId) {
        // 1.组织数据
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setId(id);
        userCoupon.setCouponId(coupon.getId());
        userCoupon.setUserId(userId);
        // 2.有效期计算
        Integer termDays = coupon.getTermDays();
        if (termDays == null || termDays == 0) {
            // 绝对有效期，由优惠券统一指定
            userCoupon.setTermBeginTime(coupon.getTermBeginTime());
            userCoupon.setTermEndTime(coupon.getTermEndTime());
        } else {
            // 相对有效期，从领取之日起的N天
            LocalDateTime now = LocalDateTime.now();
            userCoupon.setTermBeginTime(now);
            userCoupon.setTermEndTime(now.plusDays(termDays));
        }
        // 3.写入数据库
        save(userCoupon);
    }

    @Override
    public void writeOffCoupon(Long couponId, Long orderId) {
        // 1.查询优惠券
        UserCoupon coupon = getById(couponId);
        AssertUtils.isNotNull(coupon, PromotionErrorInfo.COUPON_NOT_EXISTS);
        // 2.判断状态
        AssertUtils.isTrue(UserCouponStatus.UNUSED.equalsValue(coupon.getStatus()),
                PromotionErrorInfo.INVALID_USER_COUPON);
        // 3.判断有效期
        LocalDateTime now = LocalDateTime.now();
        AssertUtils.isTrue(now.isAfter(
                coupon.getTermBeginTime()) && now.isBefore(coupon.getTermEndTime()),
                PromotionErrorInfo.COUPON_EXPIRED);
        // 4.核销，修改优惠券状态
        lambdaUpdate()
                .set(UserCoupon::getStatus, UserCouponStatus.USED.getValue())
                .set(UserCoupon::getUsedTime, now)
                .eq(UserCoupon::getId, couponId)
                .update();
    }

    @Override
    public void refundCoupon(Long couponId) {
        // 1.查询优惠券
        UserCoupon coupon = getById(couponId);
        AssertUtils.isNotNull(coupon, PromotionErrorInfo.COUPON_NOT_EXISTS);

        // 2.判断状态
        if (!UserCouponStatus.USED.equalsValue(coupon.getStatus())) {
            // 不是已使用状态，无需处理
            return;
        }

        // 3.判断有效期，是否已经过期
        LocalDateTime now = LocalDateTime.now();
        // 如果过期，则状态为过期，否则状态为 未使用
        UserCouponStatus status = now.isAfter(coupon.getTermEndTime()) ?
                UserCouponStatus.EXPIRED : UserCouponStatus.UNUSED;

        // 4.修改优惠券状态
        lambdaUpdate()
                .set(UserCoupon::getStatus, status)
                .eq(UserCoupon::getId, couponId)
                .update();
    }

}
