package com.tianji.remark.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.dto.remark.LikedTimesDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.contants.RedisConstants;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author lusy
 * @since 2025-03-13
 */
@Service
@RequiredArgsConstructor
public class LikedRecordServiceRedisImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final RabbitMqHelper rabbitMqHelper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void addLikeRecord(LikeRecordFormDTO recordFormDTO) {

        // 获取当前用户的 userId
        Long userId = UserContext.getUser();

        Boolean success = recordFormDTO.getLiked() ? like(userId, recordFormDTO) : cancelLike(userId, recordFormDTO);

        if (!success) {
            return;
        }

        /* Integer count = lambdaQuery().eq(LikedRecord::getBizId, recordFormDTO.getBizId()).count();
        // 发送通知
        rabbitMqHelper.send(
                MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
                MqConstants.Key.QA_LIKED_TIMES_KEY,
                LikedTimesDTO.of(recordFormDTO.getBizId(), count)
        );*/

        // 如果执行成功，统计点赞数
        Long likedTime = redisTemplate.opsForSet()
                .size(RedisConstants.LIKE_BIZ_KEY_PREFIX + recordFormDTO.getBizId().toString());

        // 缓存点赞总数到redis
        redisTemplate.opsForZSet().add(
                RedisConstants.LIKES_TIMES_KEY_PREFIX + recordFormDTO.getBizType(),
                recordFormDTO.getBizId().toString(),
                likedTime
        );
    }

    @Override
    public Set<Long> isBizLiked(List<Long> bizIds) {

        /* // 获取当前用户的 userId
        Long userId = UserContext.getUser();

        // 查询当前用户是否点赞过
        List<LikedRecord> list = lambdaQuery()
                .eq(LikedRecord::getUserId, userId)
                .in(LikedRecord::getBizId, bizIds)
                .list();

        return list.stream().map(LikedRecord::getBizId).collect(Collectors.toSet());*/

        // 1.获取登录用户id
        Long userId = UserContext.getUser();
        // 2.查询点赞状态
        List<Object> objects = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection src = (StringRedisConnection) connection;
            for (Long bizId : bizIds) {
                String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + bizId;
                src.sIsMember(key, userId.toString());
            }
            return null;
        });
        // 3.返回结果
        return IntStream.range(0, objects.size()) // 创建从0到集合size的流
                .filter(i -> (boolean) objects.get(i)) // 遍历每个元素，保留结果为true的角标i
                .mapToObj(bizIds::get)// 用角标i取bizIds中的对应数据，就是点赞过的id
                .collect(Collectors.toSet());// 收集
    }

    @Override
    public void readLikedTimesAndSendMessage(String bizType, int maxBizSize) {
        // 读取并移除redis中缓存的点赞总数
        String key = RedisConstants.LIKES_TIMES_KEY_PREFIX + bizType;
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().popMin(key, maxBizSize);
        if (CollUtil.isEmpty(tuples)) {
            return;
        }
        // 封装数据
        List<LikedTimesDTO> list = new ArrayList<>(tuples.size());

        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String bizId = tuple.getValue();
            Long likedTimes = tuple.getScore().longValue();
            if (bizId == null || likedTimes == null) {
                continue;
            }
            list.add(LikedTimesDTO.of(Long.parseLong(bizId), likedTimes.intValue()));
        }

        // 发送消息
        rabbitMqHelper.send(
                MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
                StrUtil.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE, bizType),
                list
        );
    }

    private Boolean cancelLike(Long userId, LikeRecordFormDTO recordFormDTO) {
        // return remove(new QueryWrapper<LikedRecord>().lambda()
        //         .eq(LikedRecord::getUserId, userId)
        //         .eq(LikedRecord::getBizId, recordFormDTO.getBizId()));
        String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + recordFormDTO.getBizId().toString();
        Long result = redisTemplate.opsForSet().remove(key, userId.toString());
        return result != null && result > 0;
    }


    private Boolean like(Long userId, LikeRecordFormDTO recordFormDTO) {
        /*
        // 查询点赞记录
        LikedRecord record = lambdaQuery()
                .eq(LikedRecord::getBizId, recordFormDTO.getBizId())
                .eq(LikedRecord::getUserId, userId)
                .one();

        // 如果已经点赞过了，直接返回 false
        if (ObjectUtil.isNotEmpty(record)) {
            return false;
        }
        // 如果未点过赞，新增点赞记录
        record = new LikedRecord()
                .setUserId(userId)
                .setBizId(recordFormDTO.getBizId())
                .setBizType(recordFormDTO.getBizType());
        return save(record);*/
        // 1.获取Key
        String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + recordFormDTO.getBizId();
        // 2.执行SADD命令
        Long result = redisTemplate.opsForSet().add(key, userId.toString());
        return result != null && result > 0;

    }

}
