package com.tianji.promotion.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.query.CodeQuery;
import com.tianji.promotion.domain.vo.ExchangeCodeVO;
import com.tianji.promotion.service.IExchangeCodeService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * <p>
 * 兑换码 前端控制器
 * </p>
 *
 * @author lusy
 * @since 2025-03-19
 */
@Api(tags = "兑换码管理接口")
@RequiredArgsConstructor
@RestController
@RequestMapping("/codes")
public class ExchangeCodeController {

    private final IExchangeCodeService codeService;

    @ApiOperation("根据优惠券id分页查看兑换码")
    @GetMapping("/page")
    public PageDTO<ExchangeCodeVO> queryExchangeCodeByCouponId(@Valid CodeQuery query) {
        return codeService.queryExchangeCodeByCouponId(query);
    }


}
