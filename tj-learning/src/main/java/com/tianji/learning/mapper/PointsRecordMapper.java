package com.tianji.learning.mapper;

import com.tianji.learning.domain.po.PointsRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 学习积分记录，每个月底清零 Mapper 接口
 * </p>
 *
 * @author lusy
 * @since 2025-03-14
 */
public interface PointsRecordMapper extends BaseMapper<PointsRecord> {

    void createPointsRecordTableBySeason(@Param("tableName") String tableName);
}
