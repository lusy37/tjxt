package com.tianji.promotion.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import com.tianji.promotion.constants.DiscountType;
import com.tianji.promotion.strategy.discount.Discount;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 优惠券的规则信息
 * </p>
 *
 * @author 虎哥
 * @since 2022-09-06
 */
@Data
@EqualsAndHashCode(callSuper = false, of = "id")
@Accessors(chain = true)
@TableName("coupon")
public class Coupon implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 优惠券id
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 优惠券名称，可以和活动名称保持一致
     */
    private String name;

    /**
     * 优惠券类型，1：普通券。目前就一种，保留字段
     */
    private Integer type;

    /**
     * 折扣类型，1：满减，2：折扣，3：无门槛
     */
    private Integer discountType;

    /**
     * 是否限定作用范围，false：不限定，true：限定。默认false
     */
    @TableField("`specific`")
    private Boolean specific;

    /**
     * 折扣值，如果是满减则存满减金额，如果是折扣，则存折扣率，8折就是存80
     */
    private Integer discountValue;

    /**
     * 使用门槛，0：表示无门槛，其他值：最低消费金额
     */
    private Integer thresholdAmount;

    /**
     * 最高优惠金额，满减最大，0：表示没有限制，不为0，则表示该券有金额的限制
     */
    private Integer maxDiscountAmount;

    /**
     * 优惠折扣方案的拓展参数，目前为空
     */
    private String extParam;

    /**
     * 获取方式：1：手动领取，2：兑换码
     */
    private Integer obtainWay;

    /**
     * 开始发放时间
     */
    private LocalDateTime issueBeginTime;

    /**
     * 结束发放时间
     */
    private LocalDateTime issueEndTime;

    /**
     * 优惠券有效期天数，0：表示有效期是指定有效期的
     */
    private Integer termDays;

    /**
     * 优惠券有效期开始时间
     */
    private LocalDateTime termBeginTime;

    /**
     * 优惠券有效期结束时间
     */
    private LocalDateTime termEndTime;

    /**
     * 优惠券配置状态，1：待发放，2：未开始   3：进行中，4：已结束，5：暂停
     */
    private Integer status;

    /**
     * 总数量，0：表示无限量，其他正数表示最大发放量，不超过5000
     */
    private Integer totalNum;

    /**
     * 已发行数量，用于判断是否超发
     */
    private Integer issueNum;

    /**
     * 每个人限领的数量，默认1
     */
    private Integer userLimit;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 创建人
     */
    @TableField(fill = FieldFill.INSERT)
    private Long creater;

    /**
     * 更新人
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updater;

    public Discount discount(){
        return DiscountType.of(discountType).getDiscount(this);
    }
}
