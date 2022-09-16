package com.tianji.promotion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.query.CodeQuery;

/**
 * <p>
 * 兑换码 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-09-06
 */
public interface IExchangeCodeService extends IService<ExchangeCode> {

    void generateExchangeCodeAsync(Coupon coupon);

    PageDTO<String> queryCodePage(CodeQuery query);

    void exchangeCoupon(String code);
}
