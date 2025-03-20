package com.tianji.learning.service;

import com.tianji.learning.constants.PointsRecordType;
import com.tianji.learning.domain.po.PointsRecord;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.PointsStatisticsVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务类
 * </p>
 *
 * @author lusy
 * @since 2025-03-14
 */
public interface IPointsRecordService extends IService<PointsRecord> {

    void addPointsRecord(Long userId, Integer points, PointsRecordType pointsRecordType);

    List<PointsStatisticsVO> queryMyPointsToday();

    void createPointsRecordTableBySeason(Integer season);

    List<PointsRecord> queryPointsRecordListByPage(int pageNo, int pageSize, LocalDateTime time);
}
