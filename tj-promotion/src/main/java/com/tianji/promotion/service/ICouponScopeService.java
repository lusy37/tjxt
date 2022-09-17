package com.tianji.promotion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.strategy.scope.Scope;

import java.util.List;

/**
 * <p>
 * 优惠券作用范围信息 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-09-06
 */
public interface ICouponScopeService extends IService<CouponScope> {

    void removeByCouponId(Long couponId);

    List<Scope> queryScopeByCouponId(Long couponId);
}
