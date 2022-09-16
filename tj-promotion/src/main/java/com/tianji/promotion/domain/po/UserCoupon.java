package com.tianji.promotion.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息
 * </p>
 *
 * @author 虎哥
 * @since 2022-09-06
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user_coupon")
public class UserCoupon implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 优惠券id
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 优惠券的拥有者
     */
    private Long userId;

    /**
     * 优惠券配置id
     */
    private Long couponId;

    /**
     * 优惠券可以使用结束时间
     */
    private LocalDateTime termBeginTime;

    /**
     * 优惠券开始使用时间
     */
    private LocalDateTime termEndTime;

    /**
     * 使用时间
     */
    private LocalDateTime usedTime;

    /**
     * 使用该券的订单id
     */
    private String orderId;

    /**
     * 优惠券状态，1：未使用，2：已使用，3：已失效
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;


}
