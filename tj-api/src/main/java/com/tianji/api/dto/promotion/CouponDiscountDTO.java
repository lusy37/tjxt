package com.tianji.api.dto.promotion;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@ApiModel(description = "订单的可用优惠券及折扣信息")
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CouponDiscountDTO {
    @ApiModelProperty("优惠券id")
    private Long id;
    @ApiModelProperty("优惠券规则")
    private String rule;
    @ApiModelProperty("本订单最大优惠金额")
    private Integer discountAmount;
}