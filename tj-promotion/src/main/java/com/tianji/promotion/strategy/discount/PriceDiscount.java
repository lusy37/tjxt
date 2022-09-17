package com.tianji.promotion.strategy.discount;

import com.tianji.common.utils.StringUtils;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PriceDiscount implements Discount{

    /**
     * 折扣值，如果是满减则存满减金额，如果是折扣，则存折扣率，8折就是存80
     */
    private final int discountValue;

    /**
     * 使用门槛，0：表示无门槛，其他值：最低消费金额
     */
    private final int thresholdAmount;

    private static final String RULE_TEMPLATE = "满{}元减{}元";


    @Override
    public boolean canUse(int totalAmount) {
        return totalAmount >= thresholdAmount;
    }

    @Override
    public int calculateDiscount(int totalAmount) {
        return discountValue;
    }

    @Override
    public String getRule() {
        return StringUtils.format(RULE_TEMPLATE, thresholdAmount, discountValue);
    }
}
