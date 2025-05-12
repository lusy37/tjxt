package com.tianji.remark.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.dto.remark.LikedTimesDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.domain.po.LikedStat;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.mapper.LikedStatMapper;
import com.tianji.remark.service.ILikedStatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LikedStatServiceImpl extends ServiceImpl<LikedStatMapper, LikedStat> implements ILikedStatService {

    private final RabbitMqHelper rabbitMqHelper;

    @Override
    public void updateLikedTimes(String bizType, List<LikedTimesDTO> msg) {
        // 1.更新本地数据库
        List<Long> bizIds = msg.stream().map(LikedTimesDTO::getBizId).collect(Collectors.toList());
        // 1.1.查询旧数据
        List<LikedStat> oldStats = lambdaQuery().in(LikedStat::getBizId, bizIds).list();
        Map<Long, LikedStat> oldStatMap = new HashMap<>(0);
        if(CollUtils.isNotEmpty(oldStats)) {
            oldStatMap = oldStats.stream().collect(Collectors.toMap(LikedStat::getBizId, e  -> e));
        }
        // 1.2.处理为PO
        List<LikedStat> list = new ArrayList<>(msg.size());
        for (LikedTimesDTO dto : msg) {
            // 判断bizId是否已经存在
            LikedStat stat = oldStatMap.get(dto.getBizId());
            if(stat == null){
                // 不存在，新增
                stat = new LikedStat();
                stat.setBizId(dto.getBizId());
                stat.setLikedTimes(dto.getLikedTimes());
                stat.setBizType(bizType);
            }else{
                // 已经存在，需要更新
                stat.setLikedTimes(dto.getLikedTimes());
            }
            list.add(stat);
        }
        // 1.3.批处理
        saveOrUpdateBatch(list);

        // 2.发送MQ消息
        rabbitMqHelper.send(
                MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
                StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE, bizType),
                msg);
    }
}
