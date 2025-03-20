package com.tianji.promotion.domain.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class ExchangeCodeVO {

    @ApiModelProperty(value = "兑换码id")
    private Integer id;

    @ApiModelProperty(value = "兑换码")
    private String code;

}
