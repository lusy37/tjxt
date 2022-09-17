package com.tianji.promotion.strategy.discount;

import com.tianji.common.utils.StringUtils;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PerPriceDiscount implements Discount{
    /**
     * 折扣值，如果是满减则存满减金额，如果是折扣，则存折扣率，8折就是存80
     */
    private final int discountValue;

    /**
     * 使用门槛，0：表示无门槛，其他值：最低消费金额
     */
    private final int thresholdAmount;

    /**
     * 最高优惠金额，满减最大，0：表示没有限制，不为0，则表示该券有金额的限制
     */
    private final int maxDiscountAmount;

    private final static String RULE_TEMPLATE = "每满{}元减{}元，不超过{}元";

    @Override
    public boolean canUse(int totalAmount) {
        return totalAmount >= thresholdAmount;
    }

    @Override
    public int calculateDiscount(int totalAmount) {
        int discount = 0;
        while (totalAmount >= thresholdAmount){
            discount += discountValue;
            totalAmount -= thresholdAmount;
        }
        return Math.min(discount, maxDiscountAmount);
    }

    @Override
    public String getRule() {
        return StringUtils.format(RULE_TEMPLATE, thresholdAmount, discountValue, maxDiscountAmount);
    }
}
