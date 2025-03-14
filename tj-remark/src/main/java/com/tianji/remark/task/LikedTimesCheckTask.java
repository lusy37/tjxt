package com.tianji.remark.task;

import com.tianji.remark.config.TypeProperties;
import com.tianji.remark.service.ILikedRecordService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.rowset.CachedRowSet;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class LikedTimesCheckTask {

    private final TypeProperties typeProperties;
    private static final int MAX_BIZ_SIZE = 30;
    
    private final ILikedRecordService likedRecordService;

    /*    @Scheduled(fixedDelay = 20000)
    public void checkLikedTimes() {
        for (String bizType : BIZ_TYPES) {
            likedRecordService.readLikedTimesAndSendMessage(bizType, MAX_BIZ_SIZE);
        }
    }*/

    @XxlJob("checkLikedTimes")
    public void checkLikedTimes() {
        log.info("开始检查点赞数");
        System.out.println(typeProperties.getBIZ_TYPES().toString());
        for (String bizType : typeProperties.getBIZ_TYPES()) {
            likedRecordService.readLikedTimesAndSendMessage(bizType, MAX_BIZ_SIZE);
        }
    }

}
