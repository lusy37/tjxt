package com.tianji.promotion.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.pojo.Coupon;
import com.tianji.promotion.domain.pojo.ExchangeCode;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.promotion.domain.query.CodeQuery;
import com.tianji.promotion.domain.vo.ExchangeCodeVO;

/**
 * <p>
 * 兑换码 服务类
 * </p>
 *
 * @author lusy
 * @since 2025-03-19
 */
public interface IExchangeCodeService extends IService<ExchangeCode> {

    void asyncGenerateExchangeCode(Coupon coupon);

    PageDTO<ExchangeCodeVO> queryExchangeCodeByCouponId(CodeQuery query);

    boolean updateExchangeMark(long serialNum, boolean mark);

    Long exchangeTargetId(long serialNum);
}
