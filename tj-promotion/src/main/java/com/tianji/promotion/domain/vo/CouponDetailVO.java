package com.tianji.promotion.domain.vo;

import com.tianji.common.utils.DateUtils;
import com.tianji.promotion.domain.dto.CouponScopeDTO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

@Data
@ApiModel(description = "优惠券详细数据")
public class CouponDetailVO {
    @ApiModelProperty("优惠券id，新增不需要添加，更新必填")
    private Long id;

    @ApiModelProperty("优惠券名称")
    private String name;

    @ApiModelProperty("优惠券使用范围，key范围类型(0-没有限制，1-限定分类，2-限定课程)，值是范围ID")
    private List<CouponScopeDTO> scopes;

    @ApiModelProperty("优惠券折扣规则")
    private String rule;

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

    @ApiModelProperty("优惠券总量，如果为0代表无上限")
    private Integer totalNum;
    @ApiModelProperty("每人领取的上限")
    private Integer userLimit;
    @ApiModelProperty("获取方式1：手动领取，2：指定发放（通过兑换码兑换）")
    private Integer obtainWay;
}
