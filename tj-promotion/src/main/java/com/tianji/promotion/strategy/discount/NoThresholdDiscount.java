package com.tianji.promotion.strategy.discount;

import com.tianji.common.utils.StringUtils;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class NoThresholdDiscount implements Discount{
    /**
     * 折扣值，如果是满减则存满减金额，如果是折扣，则存折扣率，8折就是存80
     */
    private final int discountValue;

    private static final String RULE_TEMPLATE = "无门槛抵扣{}元";

    @Override
    public boolean canUse(int totalAmount) {
        return true;
    }

    @Override
    public int calculateDiscount(int totalAmount) {
        return discountValue;
    }

    @Override
    public String getRule() {
        return StringUtils.format(RULE_TEMPLATE, discountValue);
    }
}
