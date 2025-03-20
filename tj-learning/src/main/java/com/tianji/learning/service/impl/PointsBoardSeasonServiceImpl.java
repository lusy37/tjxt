package com.tianji.learning.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.tianji.common.utils.CollUtils;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.domain.vo.PointsBoardSeasonVO;
import com.tianji.learning.mapper.PointsBoardSeasonMapper;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author lusy
 * @since 2025-03-14
 */
@Service
public class PointsBoardSeasonServiceImpl extends ServiceImpl<PointsBoardSeasonMapper, PointsBoardSeason> implements IPointsBoardSeasonService {

    @Override
    public List<PointsBoardSeasonVO> pointsBoardSeasonList() {

        // 1.获取时间
        LocalDateTime now = LocalDateTime.now();

        List<PointsBoardSeason> list = lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, now).list();
        if (CollUtils.isEmpty(list)) {
            return null;
        }
        return BeanUtil.copyToList(list, PointsBoardSeasonVO.class);
    }

    @Override
    public Integer querySeasonByTime(LocalDateTime time) {
        PointsBoardSeason season = lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, time)
                .ge(PointsBoardSeason::getEndTime, time)
                .one();
        return season == null ? null : season.getId();
    }

}
