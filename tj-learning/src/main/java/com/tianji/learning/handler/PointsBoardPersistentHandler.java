package com.tianji.learning.handler;

import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.service.IPointsRecordService;
import com.tianji.learning.utils.TableInfoContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import io.swagger.models.auth.In;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.tianji.learning.constants.LearningConstants.POINTS_BOARD_KEY_PREFIX;
import static com.tianji.learning.constants.LearningConstants.POINTS_RECORD_TABLE_PREFIX;

@Component
@Slf4j
@RequiredArgsConstructor
public class PointsBoardPersistentHandler {

    private final IPointsBoardSeasonService pointsBoardSeasonService;

    private final IPointsBoardService pointsBoardService;

    private final IPointsRecordService pointsRecordService;

    private final StringRedisTemplate redisTemplate;

    // @Scheduled(cron = "0 0 3 1 * ?")
    @XxlJob("createTableJob")
    public void createPointsBoardTableOfLastSeason() {
        log.info("创建积分榜数据表");
        // 获取上一个月的时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 查询赛季 id
        Integer season = pointsBoardSeasonService.querySeasonByTime(time);
        if (season == null) {
            log.info("没有查询到上一个月的赛季");
            return;
        }
        // 3.创建表
        pointsBoardService.createPointsBoardTableBySeason(season);
    }

    // 持久化积分榜数据
    @XxlJob("savePointsBoard2DB")
    public void savePointsBoard2DB() {
        log.info("持久化积分榜数据");
        // 1. 获取上月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 2.计算动态表名
        // 2.1.查询赛季信息
        Integer season = pointsBoardSeasonService.querySeasonByTime(time);
        // 2.2.将表名存入ThreadLocal
        TableInfoContext.setInfo(POINTS_BOARD_KEY_PREFIX + season);
        // 3.查询榜单数据
        // 3.1.拼接KEY
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + time.format(DateUtils.POINTS_SUFFIX_FORMATTER);
        // 3.2.查询数据
        // 3.2.查询数据
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();
        int pageNo = shardIndex + 1;  // 起始页，就是分片序号+1 , sharedIndex 从0开始
        int pageSize = 10;
        while (true) {
            List<PointsBoard> boardList = pointsBoardService.queryCurrentBoardList(key, pageNo, pageSize);
            if (CollUtils.isEmpty(boardList)) {
                break;
            }
            // 4.持久化到数据库
            // 4.1.把排名信息写入id
            boardList.forEach(board -> {
                board.setId(Long.valueOf(board.getRank()));
                board.setRank(null);
            });
            // 4.2.持久化
            pointsBoardService.saveBatch(boardList);
            // 5.翻页
            pageNo+=shardTotal;// 跳过N个页，N就是分片数量
        }
        // 任务结束，移除动态表名
        TableInfoContext.remove();
    }

    @XxlJob("clearPointsBoardFromRedis")
    public void clearPointsBoardFromRedis() {
        log.info("清理积分榜数据");
        // 1. 获取上月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 2.拼接KEY
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + time.format(DateUtils.POINTS_SUFFIX_FORMATTER);
        // 3.删除
        redisTemplate.unlink(key);
    }

    @XxlJob("createPointsRecordTableJob")
    public void createPointsRecordTableOfLastSeason() {
        log.info("创建积分记录数据表");
        // 获取上一个月的时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 查询赛季 id
        Integer season = pointsBoardSeasonService.querySeasonByTime(time);
        if (season == null) {
            log.info("没有查询到上一个月的赛季");
            return;
        }
        pointsRecordService.createPointsRecordTableBySeason(season);
    }

    @XxlJob("savePointsRecord2DB")
    public void savePointsRecord2DB() {
        log.info("持久化积分记录数据");
        // 查询上个月的时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 查询赛季 id
        Integer season = pointsBoardSeasonService.querySeasonByTime(time);
        // 分页持久化
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();
        int pageNo = shardIndex + 1;
        int pageSize = 10;
        // 翻页查询
        while (true) {
            List<PointsRecord> recordList = pointsRecordService.queryPointsRecordListByPage(pageNo, pageSize, time);
            if (CollUtils.isEmpty(recordList)) {
                break;
            }
            Set<Long> ids = recordList.stream().map(PointsRecord::getId).collect(Collectors.toSet());
            // 持久化
            // 动态设置表名
            TableInfoContext.setInfo(POINTS_RECORD_TABLE_PREFIX  + season);
            pointsRecordService.saveBatch(recordList);
            // 删除持久化的数据
            CompletableFuture.runAsync(() -> {
                try {
                    System.out.println("ids = " + ids);
                    pointsRecordService.removeByIds(ids); // 假设存在此方法
                } catch (Exception e) {
                    log.error("异步删除积分记录失败，ids={}", ids, e);
                }
            });
            // 翻页
            pageNo+=shardTotal;
            TableInfoContext.remove();
        }

    }

}
