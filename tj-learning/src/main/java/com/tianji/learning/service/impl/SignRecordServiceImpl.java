package com.tianji.learning.service.impl;

import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.mq.msg.SignInMessage;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SignRecordServiceImpl implements ISignRecordService {

    private final StringRedisTemplate redisTemplate;
    private final RabbitMqHelper rabbitMqHelper;

    @Override
    public SignResultVO addSignRecords() {

        // 获取当前用户 id
        Long userId = UserContext.getUser();
        // 获取当天的起始和结束时间
        LocalDateTime now = LocalDateTime.now();
        // 拼接 Redis 键值
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId + now.format(DateUtils.SIGN_DATE_SUFFIX_FORMATTER);
        // 获取当天日期偏移量
        int offset = now.getDayOfMonth() - 1;
        // 向 Redis 中添加签到记录
        Boolean exists = redisTemplate.opsForValue().setBit(key, offset, true);
        // 判断是否已经签到
        if (exists) {
            throw new BizIllegalException("不允许重复签到！");
        }

        // 计算连续签到天数
        int signDays = countSignDays(key, offset);
        // 计算签到积分
        int rewardPoints = 0;
        switch (signDays) {
            case 7:
                rewardPoints = 10;
                break;
            case 14:
                rewardPoints = 20;
                break;
            case 28:
                rewardPoints = 40;
                break;
        }

        // 保存积分明细记录
        rabbitMqHelper.send(
                MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.SIGN_IN,
                SignInMessage.of(userId, rewardPoints + 1) // 签到积分是基本得分+奖励积分
        );

        // 返回签到结果
        SignResultVO signResultVO = new SignResultVO();
        signResultVO.setSignDays(signDays);
        signResultVO.setRewardPoints(rewardPoints);
        return signResultVO;

    }

    @Override
    public List<Byte> getSignRecords() {

        // 获取当前用户 id
        Long userId = UserContext.getUser();
        // 获取当天的起始和结束时间
        LocalDateTime now = LocalDateTime.now();
        // 拼接 Redis 键值
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId + now.format(DateUtils.SIGN_DATE_SUFFIX_FORMATTER);
        // 获取当天日期偏移量
        int dayOfMonth = now.getDayOfMonth();
        // 获取签到记录
        List<Long> result = redisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));

        int offset = dayOfMonth - 1;
        List<Byte> bytes = new ArrayList<>(Collections.nCopies(offset + 1, (byte) 0));

        if (CollUtils.isEmpty(result)) {
            return bytes;
        }

        int num = result.get(0).intValue();

        // 3.遍历数组，将每一位的值存入 Byte 数组
        while (num > 0) {
            bytes.set(offset, (byte) (num & 1));
            num = num >> 1;
            offset--;
        }

        return bytes;
    }

    private int countSignDays(String key, int offset) {
        // 计算连续签到天数
        List<Long> result = redisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create().get(
                BitFieldSubCommands.BitFieldType.unsigned(offset)).valueAt(0));

        if (CollUtils.isEmpty(result)) {
            return 0;
        }

        int num = result.get(0).intValue();
        // 2.定义一个计数器
        int count = 0;

        while ((num & 1) == 1) {
            count++;
            num = num >> 1;
        }

        return count;
    }
}
