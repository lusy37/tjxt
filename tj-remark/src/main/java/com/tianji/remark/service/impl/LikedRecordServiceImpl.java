/*
package com.tianji.remark.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.api.dto.remark.LikedTimesDTO;
import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

*/
/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author lusy
 * @since 2025-03-13
 *//*


@Service
@RequiredArgsConstructor
public class LikedRecordServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final RabbitMqHelper rabbitMqHelper;

    @Override
    public void addLikeRecord(LikeRecordFormDTO recordFormDTO) {

        // 获取当前用户的 userId
        Long userId = UserContext.getUser();

        Boolean success = recordFormDTO.getLiked() ? like(userId, recordFormDTO) : cancelLike(userId, recordFormDTO);

        if (!success) {
            return;
        }

        // 如果执行成功，统计点赞数
        Integer count = lambdaQuery().eq(LikedRecord::getBizId, recordFormDTO.getBizId()).count();
        // 发送通知
        rabbitMqHelper.send(
                MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
                MqConstants.Key.QA_LIKED_TIMES_KEY,
                LikedTimesDTO.of(recordFormDTO.getBizId(), count)
        );
    }

    @Override
    public Set<Long> isBizLiked(List<Long> bizIds) {

        // 获取当前用户的 userId
        Long userId = UserContext.getUser();

        // 查询当前用户是否点赞过
        List<LikedRecord> list = lambdaQuery()
                .eq(LikedRecord::getUserId, userId)
                .in(LikedRecord::getBizId, bizIds)
                .list();

        return list.stream().map(LikedRecord::getBizId).collect(Collectors.toSet());
    }

    private Boolean cancelLike(Long userId, LikeRecordFormDTO recordFormDTO) {
        return remove(new QueryWrapper<LikedRecord>().lambda()
                .eq(LikedRecord::getUserId, userId)
                .eq(LikedRecord::getBizId, recordFormDTO.getBizId()));
    }


    private Boolean like(Long userId, LikeRecordFormDTO recordFormDTO) {
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
        return save(record);
    }

}
*/
