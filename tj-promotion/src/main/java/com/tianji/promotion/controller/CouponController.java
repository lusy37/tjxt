package com.tianji.promotion.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.service.ICouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 优惠券的规则信息 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2022-09-06
 */
@Api(tags = "优惠券相关接口")
@RequiredArgsConstructor
@RestController
@RequestMapping("/coupons")
public class CouponController {

    private final ICouponService couponService;

    @ApiOperation("新增优惠券")
    @PostMapping
    public void saveCoupon(@Valid @RequestBody CouponFormDTO couponDTO){
        couponService.saveCoupon(couponDTO);
    }

    @ApiOperation("修改优惠券")
    @PutMapping("{id}")
    public void updateCoupon(
            @ApiParam("优惠券id") @PathVariable("id") Long id,
            @RequestBody CouponFormDTO couponDTO){
        couponDTO.setId(id);
        couponService.updateCoupon(couponDTO);
    }

    @ApiOperation("删除优惠券")
    @DeleteMapping("{id}")
    public void deleteById(@ApiParam("优惠券id") @PathVariable("id") Long id) {
        couponService.deleteById(id);
    }

    @ApiOperation("暂停优惠券发放")
    @PutMapping("/{id}/pause")
    public void pauseIssue(@ApiParam("优惠券id") @PathVariable("id") Long id) {
        couponService.pauseIssue(id);
    }


    @ApiOperation("发放优惠券")
    @PutMapping("/{id}/begin")
    public void beginIssue(@Valid @RequestBody CouponIssueFormDTO couponIssueDTO) {
        couponService.beginIssue(couponIssueDTO);
    }

    @ApiOperation("分页查询优惠券")
    @GetMapping("page")
    public PageDTO<CouponPageVO> queryCouponPage(CouponQuery query){
        return couponService.queryCouponPage(query);
    }

    @ApiOperation("根据id查询优惠券")
    @GetMapping("{id}")
    public CouponDetailVO queryCouponById(@ApiParam("优惠券id") @PathVariable("id") Long id){
        return couponService.queryCouponById(id);
    }

    @ApiOperation("用户领取指定优惠券")
    @PostMapping("{couponId}/user")
    public void snapUpCoupon(@ApiParam("优惠券id") @PathVariable("couponId") Long couponId){
        couponService.snapUpCoupon(couponId);
    }
}
