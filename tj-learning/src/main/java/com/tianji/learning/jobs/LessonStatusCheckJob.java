package com.tianji.learning.jobs;

import com.tianji.learning.constants.LessonStatus;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LessonStatusCheckJob {

    private final ILearningLessonService learningLessonService;

    @Scheduled(cron = "0 * * * * ?")
    public void lessonStatusCheck() {
        log.info("定期执行课程状态检查任务");
        // 查询状态为未过期的课程，不区分用户
        List<LearningLesson> list = learningLessonService.lambdaQuery()
                .ne(LearningLesson::getStatus, LessonStatus.EXPIRED)
                .list();
        // 判断是否过期 过期时间是不是小于当前时间
        LocalDateTime now = LocalDateTime.now();
        for (LearningLesson lesson : list) {
            if (lesson.getExpireTime().isBefore(now)) {
                lesson.setStatus(LessonStatus.EXPIRED);
            }
        }
        // 批量更新
        learningLessonService.updateBatchById(list);
    }

}
