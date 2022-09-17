package com.tianji.promotion.domain.vo;

import com.tianji.common.utils.DateUtils;
import com.tianji.promotion.constants.ObtainType;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
@ApiModel(description = "优惠券分页数据")
public class CouponPageVO {
    @ApiModelProperty("优惠券id，新增不需要添加，更新必填")
    private Long id;
    @ApiModelProperty("优惠券名称")
    private String name;
    @ApiModelProperty("使用范围说明")
    private String scope;
    @ApiModelProperty("优惠券规则")
    private String rule;
    @ApiModelProperty("获取方式1：手动领取，2：指定发放（通过兑换码兑换）")
    private ObtainType obtainWay;

    @ApiModelProperty("已使用")
    private Integer used;
    @ApiModelProperty("已发放数量")
    private Integer issueNum;
    @ApiModelProperty("优惠券总量，如果为0代表无上限")
    private Integer totalNum;

    @ApiModelProperty("发放开始时间")
    @DateTimeFormat(pattern = DateUtils.DEFAULT_DATE_TIME_FORMAT)
    private LocalDateTime issueBeginTime;
    @ApiModelProperty("发放结束时间")
    @DateTimeFormat(pattern = DateUtils.DEFAULT_DATE_TIME_FORMAT)
    private LocalDateTime issueEndTime;

    @ApiModelProperty("有效天数")
    private Integer termDays;
    @ApiModelProperty("使用有效期开始时间")
    @DateTimeFormat(pattern = DateUtils.DEFAULT_DATE_TIME_FORMAT)
    private LocalDateTime termBeginTime;
    @ApiModelProperty("使用有效期结束时间")
    @DateTimeFormat(pattern = DateUtils.DEFAULT_DATE_TIME_FORMAT)
    private LocalDateTime termEndTime;

    @ApiModelProperty("状态")
    private Integer status;
}
