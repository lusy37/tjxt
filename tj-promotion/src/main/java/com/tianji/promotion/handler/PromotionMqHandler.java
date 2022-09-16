package com.tianji.promotion.handler;

import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.service.ICouponService;
import com.tianji.promotion.service.IUserCouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.tianji.common.constants.MqConstants.Exchange.PROMOTION_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.COUPON_RECEIVE;

@Slf4j
@Component
@RequiredArgsConstructor
public class PromotionMqHandler {

    private final ICouponService couponService;
    private final IUserCouponService userCouponService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "queue.coupon.receive", durable = "true"),
            exchange = @Exchange(name = PROMOTION_EXCHANGE),
            key = COUPON_RECEIVE
    ))
    public void listenCouponReceive(UserCoupon userCoupon){
        if (userCoupon == null) {
            return;
        }
        log.debug("处理优惠券抢购消息，用户id：{}， 券id：{}，唯一token：{}",
                userCoupon.getUserId(), userCoupon.getCouponId(), userCoupon.getId());
        couponService.snapUpCoupon(userCoupon);
    }
}
