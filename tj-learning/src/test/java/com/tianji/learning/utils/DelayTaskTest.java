package com.tianji.learning.utils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.service.impl.PointsRecordServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.DelayQueue;

@Slf4j
@SpringBootTest
class DelayTaskTest {

    @Autowired
    private PointsRecordServiceImpl pointsRecordService;

    @Test
    void testDelayQueue() {

        // 初始化延迟队列
        DelayQueue<DelayTask<String>> queue = new DelayQueue<>();
        // 向延迟队列中添加任务
        log.info("开始初始化延迟任务");
        queue.add(new DelayTask<>("task1", Duration.ofSeconds(5)));
        queue.add(new DelayTask<>("task2", Duration.ofSeconds(10)));
        queue.add(new DelayTask<>("task3", Duration.ofSeconds(15)));
        log.info("尝试执行任务");
        // 从延迟队列中取出任务
        while (!queue.isEmpty()) {
            DelayTask<String> poll = null;
            try {
                poll = queue.take();
                log.info("执行任务：{}", poll.getData());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
     void queryUserPointsByTypeAndDate() {
        Map<String, Object> map = pointsRecordService.getMap(new QueryWrapper<PointsRecord>()
                .select("sum(points) as points")
                .eq("user_id", 2)
                .eq("type", 2));

        if (CollUtils.isEmpty(map)) {
            return ;
        }
        BigDecimal sumPoints = (BigDecimal) map.get("points");
        // 3.判断并返回
        System.out.println( sumPoints == null ? 0 : sumPoints.intValue() );
    }

    @Test
    public void queryMyPointsToday() {

        // 1.查询当前用户的 id 和 时间
        Long userId = UserContext.getUser();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = DateUtils.getDayStartTime(now);
        LocalDateTime endTime = DateUtils.getDayEndTime(now);
        // 2.查询今日的积分
        QueryWrapper<PointsRecord> wrapper = new QueryWrapper<PointsRecord>()
                .select("type", "sum(points) as points")
                .eq("user_id", 2)
                .groupBy("type");

        List<PointsRecord> list = pointsRecordService.list(wrapper);

        list.forEach(System.out::println);
        // 3.封装返回
        if (CollUtils.isEmpty(list)) {
            return ;
        }

        List<PointsStatisticsVO> vos = new ArrayList<>(list.size());
        for (PointsRecord p : list) {
            PointsStatisticsVO vo = new PointsStatisticsVO();
            vo.setType(p.getType().getDesc());
            vo.setMaxPoints(p.getType().getMaxPoints());
            vo.setPoints(p.getPoints());
            vos.add(vo);
        }

        for (PointsStatisticsVO vo : vos) {
            System.out.println(vo);
        }
    }

}