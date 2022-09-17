package com.tianji.promotion.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.api.dto.IdAndNumDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.UserCoupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2022-09-06
 */
public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    @Select("SELECT coupon_id AS id, COUNT(1) AS num FROM user_coupon ${ew.customSqlSegment}")
    List<IdAndNumDTO> countUsedTimes(@Param("ew") QueryWrapper<UserCoupon> wrapper);

    List<Coupon> queryMyCoupon(@Param("userId") Long userId);

    Coupon queryCouponByUserCouponId(@Param("id") Long userCouponId);
}
