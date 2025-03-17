package com.tianji.learning.mq;


import com.tianji.common.constants.MqConstants;
import com.tianji.learning.constants.PointsRecordType;
import com.tianji.learning.mq.msg.SignInMessage;
import com.tianji.learning.service.IPointsRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class LearningPointsListener {

    private final IPointsRecordService pointsRecordService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "sign.points.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LEARNING_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.SIGN_IN
    ))
    // 签到积分接口
    public void listenSignInMessage(SignInMessage signInMessage) {
        log.info("用户{}签到成功，获得{}积分", signInMessage.getUserId(), signInMessage.getPoints());

        pointsRecordService.addPointsRecord(signInMessage.getUserId(), signInMessage.getPoints(), PointsRecordType.SIGN);
    }

    // 监听新增互动问答事件
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "qa.points.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LEARNING_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.WRITE_REPLY
    ))
    public void listenWriteReplyMessage(Long userId){
        pointsRecordService.addPointsRecord(userId, 5, PointsRecordType.QA);
    }
}
