package com.tianji.promotion.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.service.ICouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * <p>
 * 优惠券的规则信息 前端控制器
 * </p>
 *
 * @author lusy
 * @since 2025-03-19
 */
@Api(tags = "优惠券管理接口")
@RequiredArgsConstructor
@RestController
@RequestMapping("/coupons")
public class CouponController {

    private final ICouponService couponService;
    @ApiOperation("新增优惠券")
    @PostMapping
    public void saveCoupon(@RequestBody @Valid CouponFormDTO couponFormDTO) {
        couponService.saveCoupon(couponFormDTO);
    }

    @ApiOperation("分页查询优惠券")
    @GetMapping("/page")
    public PageDTO<CouponPageVO> queryCouponByPage (CouponQuery query) {
        return couponService.queryCouponByPage(query);
    }

    @ApiOperation("发放优惠券")
    @PutMapping("/{id}/issue")
    public void beginIssue(@PathVariable Long id, @RequestBody @Valid CouponIssueFormDTO dto) {
        couponService.beginIssue(id, dto);
    }

    @ApiOperation("修改优惠券")
    @PutMapping("/{id}")
    public void updateCoupon(@PathVariable Long id, @RequestBody @Valid CouponFormDTO couponFormDTO) {
        couponService.updateCoupon(id, couponFormDTO);
    }

    @ApiOperation("删除优惠券")
    @DeleteMapping("/{id}")
    public void deleteCoupon(@PathVariable Long id) {
        couponService.deleteCouponById(id);
    }

    @ApiOperation("根据id查询优惠券信息")
    @GetMapping("/{id}")
    public CouponDetailVO getCouponById(@PathVariable Long id) {
        return couponService.getCouponById(id);
    }

    @ApiOperation("根据id暂停发放优惠券")
    @PutMapping("/{id}/pause")
    public void pauseIssue(@PathVariable Long id) {
        couponService.pauseIssue(id);
    }

    @ApiOperation("查询发放中的优惠券")
    @GetMapping("/list")
    public List<CouponVO> queryIssuingCoupons() {
        return couponService.queryIssuingCoupons();
    }

}
