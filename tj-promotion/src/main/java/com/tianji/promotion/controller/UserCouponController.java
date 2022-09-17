package com.tianji.promotion.controller;


import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCouponDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.query.UserCouponQuery;
import com.tianji.promotion.domain.vo.UserCouponVO;
import com.tianji.promotion.service.IUserCouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2022-09-06
 */
@Api(tags = "优惠券相关接口")
@RequiredArgsConstructor
@RestController
@RequestMapping("/coupon/user")
public class UserCouponController {

    private final IUserCouponService userCouponService;

    @ApiOperation("分页查询我的优惠券")
    @GetMapping("/page")
    public PageDTO<UserCouponVO> queryUserCouponPage(UserCouponQuery query){
        return userCouponService.queryUserCouponPage(query);
    }

    @ApiOperation("查询指定课程的可用优惠券")
    @PostMapping("/available")
    public List<CouponDiscountDTO> queryAvailableCoupon(@RequestBody List<OrderCourseDTO> orderCourses){
        return userCouponService.queryAvailableCoupon(orderCourses);
    }

    @ApiOperation("根据指定课程和优惠券计算折扣")
    @PostMapping("/discount")
    public CouponDiscountDTO queryDiscountByCouponId(
            @RequestBody OrderCouponDTO orderCouponDTO){
        return userCouponService.queryDiscountByCouponId(orderCouponDTO);
    }

    @ApiOperation("核销指定优惠券")
    @PutMapping("{couponId}/use/{orderId}")
    public void writeOffCoupon(
            @ApiParam("优惠券id") @PathVariable("couponId") Long couponId,
            @ApiParam("订单id") @PathVariable("orderId") Long orderId){
        userCouponService.writeOffCoupon(couponId, orderId);
    }

    @ApiOperation("退还指定优惠券")
    @PutMapping("{couponId}/refund")
    public void refundCoupon(@ApiParam("优惠券id") @PathVariable("couponId") Long couponId){
        userCouponService.refundCoupon(couponId);
    }

}
