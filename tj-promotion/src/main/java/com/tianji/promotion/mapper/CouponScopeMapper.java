package com.tianji.promotion.mapper;

import com.tianji.promotion.domain.po.CouponScope;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * 优惠券作用范围信息 Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2022-09-06
 */
public interface CouponScopeMapper extends BaseMapper<CouponScope> {

    @Select("SELECT type, biz_id FROM coupon_biz WHERE coupon_id = #{couponId}")
    List<CouponScope> queryBizByCouponId(Long couponId);
}
