package com.tianji.remark.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.remark.domain.po.LikedStat;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LikedStatMapper extends BaseMapper<LikedStat> {
    // 可添加自定义 SQL 方法（如需）
}
