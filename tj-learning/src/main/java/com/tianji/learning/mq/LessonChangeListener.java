package com.tianji.learning.mq;

import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class LessonChangeListener {

    private final ILearningLessonService lessonService;


    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "learning.lesson.pay.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_PAY_KEY
    ))
    public void listenLessonPay(OrderBasicDTO orderBasicDTO) {

        // 健壮性判断
        if (orderBasicDTO == null || orderBasicDTO.getUserId() == null || orderBasicDTO.getCourseIds() == null) {
            // 数据有误，无需处理
            log.error("接收到MQ消息有误，订单数据为空");
            return;
        }

        // 2.添加课程
        log.debug("监听到用户{}的订单{}，需要添加课程{}到课表中", orderBasicDTO.getUserId(), orderBasicDTO.getOrderId(), orderBasicDTO.getCourseIds());
        lessonService.addUserLessons(orderBasicDTO.getUserId(), orderBasicDTO.getCourseIds());
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "learning.lesson.refund.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_REFUND_KEY
    ))
    public void listenLessonRefund(OrderBasicDTO orderBasicDTO) {

        // 健壮性判断
        if (orderBasicDTO == null || orderBasicDTO.getUserId() == null || orderBasicDTO.getCourseIds() == null) {
            // 数据有误，无需处理
            log.error("接收到MQ消息有误，订单数据为空");
            return;
        }

        // 2.删除课程
        log.debug("监听到用户{}的订单{}，需要删除课程{}到课表中", orderBasicDTO.getUserId(), orderBasicDTO.getOrderId(), orderBasicDTO.getCourseIds());
        lessonService.deleteUserLessons(orderBasicDTO.getUserId(), orderBasicDTO.getCourseIds());
    }
}
