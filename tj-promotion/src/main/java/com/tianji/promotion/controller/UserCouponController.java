package com.tianji.promotion.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.query.UserCouponQuery;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.service.IUserCouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 前端控制器
 * </p>
 *
 * @author lusy
 * @since 2025-03-20
 */
@Api(tags = "用户优惠券管理接口")
@RequiredArgsConstructor
@RestController
@RequestMapping("/user-coupons")
public class UserCouponController {

    private final IUserCouponService userCouponService;

    @ApiOperation("用户领取优惠券")
    @PostMapping("/{id}/receive")
    public void receiveCoupon(@PathVariable Long id) {
        userCouponService.receiveCoupon(id);
    }

    @ApiOperation("兑换码兑换优惠券接口")
    @PostMapping("/{code}/exchange")
    public void exchangeCoupon(@PathVariable("code") String code){
        userCouponService.exchangeCoupon(code);
    }

    @ApiOperation("查询我的优惠券")
    @GetMapping("/page")
    public PageDTO<CouponPageVO> queryMyCoupons(@Valid UserCouponQuery query) {
        return  userCouponService.queryMyCoupons(query);
    }
}
