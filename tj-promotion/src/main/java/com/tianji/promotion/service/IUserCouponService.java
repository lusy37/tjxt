package com.tianji.promotion.service;

import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCouponDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.UserCoupon;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.promotion.domain.query.UserCouponQuery;
import com.tianji.promotion.domain.vo.UserCouponVO;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-09-06
 */
public interface IUserCouponService extends IService<UserCoupon> {

    Map<Long, Integer> countUsedTimes(List<Long> couponIds);

    PageDTO<UserCouponVO> queryUserCouponPage(UserCouponQuery query);

    List<CouponDiscountDTO> queryAvailableCoupon(List<OrderCourseDTO> orderCourses);

    CouponDiscountDTO queryDiscountByCouponId(OrderCouponDTO orderCouponDTO);

    int countUserReceiveNum(Long couponId, Long userId);

    void createUserCouponWithId(Coupon coupon, Long id, Long userId);

    void writeOffCoupon(Long couponId, Long orderId);

    void refundCoupon(Long couponId);

}
