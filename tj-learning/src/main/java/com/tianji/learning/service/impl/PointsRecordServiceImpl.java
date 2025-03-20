package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.PointsRecordType;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.mapper.PointsRecordMapper;
import com.tianji.learning.service.IPointsRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.tianji.learning.constants.LearningConstants.POINTS_RECORD_TABLE_PREFIX;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务实现类
 * </p>
 *
 * @author lusy
 * @since 2025-03-14
 */
@RequiredArgsConstructor
@Service
public class PointsRecordServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord> implements IPointsRecordService {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void addPointsRecord(Long userId, Integer points, PointsRecordType pointsRecordType) {

        int maxPoints = pointsRecordType.getMaxPoints();
        // 1.判断当前方式有没有积分上限
        int realPoints = points;
        // 判断是否有积分上限
        if (maxPoints != 0) {
            // 存在积分上限，查询今日已得积分
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startTime = DateUtils.getDayStartTime(now);
            LocalDateTime endTime = DateUtils.getDayEndTime(now);

            int currentPoints = queryUserPointsByTypeAndDate(userId, pointsRecordType, startTime, endTime);

            // 2.2.判断是否超过上限
            if(currentPoints >= maxPoints) {
                // 2.3.超过，直接结束
                return;
            }
            // 2.4.没超过，保存积分记录
            if(currentPoints + points > maxPoints){
                realPoints = maxPoints - currentPoints;
            }
        }

        // 3.没有，直接保存积分记录
        PointsRecord p = new PointsRecord();
        p.setPoints(realPoints);
        p.setUserId(userId);
        p.setType(pointsRecordType);
        save(p);

        // 累加积分到 Redis 中
        LocalDateTime now = LocalDateTime.now();
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + now.format(DateUtils.POINTS_SUFFIX_FORMATTER);

        redisTemplate.opsForZSet().incrementScore(key, String.valueOf(userId), realPoints);
    }

    @Override
    public List<PointsStatisticsVO> queryMyPointsToday() {

        // 1.查询当前用户的 id 和 时间
        Long userId = UserContext.getUser();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = DateUtils.getDayStartTime(now);
        LocalDateTime endTime = DateUtils.getDayEndTime(now);
        // 2.查询今日的积分
        QueryWrapper<PointsRecord> wrapper = new QueryWrapper<PointsRecord>()
                .select("type", "sum(points) as points")
                .eq("user_id", userId)
                .between("create_time", startTime, endTime)
                .groupBy("type");

        List<PointsRecord> list = this.list(wrapper);

        // 3.封装返回
        if (CollUtils.isEmpty(list)) {
            return CollUtils.emptyList();
        }

        List<PointsStatisticsVO> vos = new ArrayList<>(list.size());
        for (PointsRecord p : list) {
            PointsStatisticsVO vo = new PointsStatisticsVO();
            vo.setType(p.getType().getDesc());
            vo.setMaxPoints(p.getType().getMaxPoints());
            vo.setPoints(p.getPoints());
            vos.add(vo);
        }
        return vos;
    }

    @Override
    public void createPointsRecordTableBySeason(Integer season) {
        this.getBaseMapper().createPointsRecordTableBySeason(POINTS_RECORD_TABLE_PREFIX + season);
    }

    @Override
    public List<PointsRecord> queryPointsRecordListByPage(int pageNo, int pageSize, LocalDateTime time) {

        Page<PointsRecord> page = new Page<>(pageNo, pageSize);
        LocalDateTime startTime = DateUtils.getMonthBeginTime(LocalDate.from(time));
        LocalDateTime endTime = DateUtils.getMonthEndTime(LocalDate.from(time));

        page = lambdaQuery()
                .between(PointsRecord::getCreateTime, startTime, endTime)
                .page(page);

        return page.getRecords();
    }

    private int queryUserPointsByTypeAndDate(
            Long userId, PointsRecordType pointsRecordType, LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> map = this.getMap(new QueryWrapper<PointsRecord>()
                .select("sum(points) as points")
                .eq("user_id", userId)
                .eq("type", pointsRecordType)
                .between("create_time", startTime, endTime));

        if (CollUtils.isEmpty(map)) {
            return 0;
        }
        BigDecimal sumPoints = (BigDecimal) map.get("points");
        // 3.判断并返回
        return sumPoints == null ? 0 : sumPoints.intValue();
    }
}
