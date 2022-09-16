package com.tianji.promotion.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;

/**
 * <p>
 * 优惠券的规则信息 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-09-06
 */
public interface ICouponService extends IService<Coupon> {

    void saveCoupon(CouponFormDTO couponDTO);

    void deleteById(Long id);

    void updateCoupon(CouponFormDTO couponDTO);

    PageDTO<CouponPageVO> queryCouponPage(CouponQuery query);

    CouponDetailVO queryCouponById(Long id);

    void pauseIssue(Long id);

    void beginIssue(CouponIssueFormDTO couponIssueDTO);

    void snapUpCoupon(Long couponId);

    void snapUpCoupon(UserCoupon userCoupon);
}
