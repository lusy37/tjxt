package com.tianji.remark.domain.po;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@ApiModel(value = "LikedStat", description = "点赞统计表实体")
public class LikedStat {

    @ApiModelProperty(value = "主键id")
    private Long id;

    @ApiModelProperty(value = "点赞数量")
    private Integer likedTimes;

    @ApiModelProperty(value = "点赞的业务id")
    private Long bizId;

    @ApiModelProperty(value = "点赞的用户id")
    private Long userId;

    @ApiModelProperty(value = "点赞的业务类型")
    private String bizType;

    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间")
    private LocalDateTime updateTime;
}
