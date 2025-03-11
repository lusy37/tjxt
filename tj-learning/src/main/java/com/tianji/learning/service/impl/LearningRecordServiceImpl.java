package com.tianji.learning.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.LessonStatus;
import com.tianji.learning.constants.SectionType;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.utils.LearningRecordDelayTaskHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author lusy
 * @since 2025-03-08
 */
@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {

    private final ILearningLessonService learningLessonService;
    private final CourseClient courseClient;
    private final LearningRecordDelayTaskHandler delayTaskHandler;

    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {

        // 判断 courseId 是否为空
        if (courseId == null) {
            return null;
        }

        // 获取当前用户id
        Long userId = UserContext.getUser();

        // 查询学习记录
        LearningLesson lesson = learningLessonService.queryByUserAndCourseId(userId, courseId);

        if (lesson == null) {
            throw new BizIllegalException("用户的课表中并没有该课程");
        }

        // 3.查询学习记录
        // select * from xx where lesson_id = #{lessonId}
        List<LearningRecord> records = lambdaQuery()
                .eq(LearningRecord::getLessonId, lesson.getId()).list();
        // 4.封装结果
        LearningLessonDTO learningLessonDTO = new LearningLessonDTO();
        learningLessonDTO.setId(lesson.getId());
        learningLessonDTO.setLatestSectionId(lesson.getLatestSectionId());
        learningLessonDTO.setRecords(BeanUtils.copyList(records, LearningRecordDTO.class));

        return learningLessonDTO;
    }

    @Override
    public void addLearningRecord(LearningRecordFormDTO recordFormDTO) {

        // 判断参数是否为空
        if (recordFormDTO == null) {
            return;
        }

        // 获取当前用户id
        Long userId = UserContext.getUser();

        Boolean finished = false;
        // 判断的小节提交类型
        if (recordFormDTO.getSectionType().equals(SectionType.VIDEO)) {
            // 视频小节
            finished = handleVideoRecord(userId, recordFormDTO);
        } else {
            // 考试小节
            finished = handleExamRecord(userId, recordFormDTO);
        }

        // 3.处理课表数据
        if (!finished) {
            return;
        }
        handleLearningLessonsChanges(recordFormDTO);
    }

    private void handleLearningLessonsChanges(LearningRecordFormDTO recordFormDTO) {

        // 查询课表
        LearningLesson lesson = learningLessonService.getById(recordFormDTO.getLessonId());

        if (lesson == null) {
            throw new BizIllegalException("课程不存在，无法更新数据！");
        }

        // 2.判断是否有新的完成小节
        boolean allLearned = false;

        // 3.如果有新完成的小节，则需要查询课程数据
        CourseFullInfoDTO cInfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if (cInfo == null) {
            throw new BizIllegalException("课程不存在，无法更新数据！");
        }
        // 4.比较课程是否全部学完：已学习小节 >= 课程总小节
        allLearned = lesson.getLearnedSections() + 1 >= cInfo.getSectionNum();


        // 5.更新课表数据
        learningLessonService.lambdaUpdate()
                .set(lesson.getStatus() == LessonStatus.NOT_BEGIN, LearningLesson::getStatus, LessonStatus.LEARNING)
                .set(allLearned, LearningLesson::getStatus, LessonStatus.FINISHED)
                .set(LearningLesson::getLatestSectionId, recordFormDTO.getSectionId())
                .set(LearningLesson::getLatestLearnTime, recordFormDTO.getCommitTime())
                // .set(finished, LearningLesson::getLearnedSections, lesson.getLearnedSections() + 1)
                .setSql("learned_sections = learned_sections + 1")
                .eq(LearningLesson::getId, lesson.getId())
                .update();
    }

    private Boolean handleExamRecord(Long userId, LearningRecordFormDTO recordFormDTO) {
        LearningRecord learningRecord = BeanUtil.copyProperties(recordFormDTO, LearningRecord.class);
        learningRecord.setUserId(userId);
        learningRecord.setFinished(true);
        learningRecord.setFinishTime(recordFormDTO.getCommitTime());

        boolean success = save(learningRecord);

        if (!success) {
            throw new DbException("新增考试记录失败");
        }

        return true;
    }

    private Boolean handleVideoRecord(Long userId, LearningRecordFormDTO recordFormDTO) {

        // 查看 redis 中是否有数据
        LearningRecord oldRecord = queryOldRecord(recordFormDTO.getLessonId(), recordFormDTO.getSectionId());
        // 判断是不是第一次提交
        // LearningRecord oldRecord = lambdaQuery()
        //         .eq(LearningRecord::getLessonId, recordFormDTO.getLessonId())
        //         .eq(LearningRecord::getSectionId, recordFormDTO.getSectionId())
        //         .one();

        if (oldRecord == null) {
            // 第一次提交
            LearningRecord learningRecord = BeanUtil.copyProperties(recordFormDTO, LearningRecord.class);
            learningRecord.setUserId(userId);
            learningRecord.setCreateTime(recordFormDTO.getCommitTime());
            // 保存到数据库
            boolean success = save(learningRecord);

            if (!success) {
                throw new DbException("新增学习记录失败");
            }

            return false;
        }

        // 存在，则更新
        // 判断是否是第一次完成
        boolean finished = !oldRecord.getFinished() && recordFormDTO.getMoment()*2 >= recordFormDTO.getDuration();

        if (!finished) {
            LearningRecord record = new LearningRecord();
            record.setLessonId(recordFormDTO.getLessonId());
            record.setSectionId(recordFormDTO.getSectionId());
            record.setMoment(recordFormDTO.getMoment());
            record.setFinished(oldRecord.getFinished());
            record.setId(oldRecord.getId());
            delayTaskHandler.addLearningRecordTask(record);
            return false;
        }

        // 第一次学完，更新数据
        boolean success = lambdaUpdate()
                .set(LearningRecord::getMoment, recordFormDTO.getMoment())
                .set(LearningRecord::getFinished, true)
                .set(LearningRecord::getFinishTime, recordFormDTO.getCommitTime())
                .eq(LearningRecord::getId, oldRecord.getId())
                .update();

        if (!success) {
            throw new DbException("更新学习记录失败");
        }

        // 清理缓存
        delayTaskHandler.cleanRecordCache(recordFormDTO.getLessonId(), recordFormDTO.getSectionId());
        return true;
    }

    private LearningRecord queryOldRecord(Long lessonId, Long sectionId) {
        // 1、读取缓存
        LearningRecord recordCache = delayTaskHandler.readRecordCache(lessonId, sectionId);
        // 2、如果命中直接返回
        if (recordCache != null) {
            return recordCache;
        }
        // 3、如果没有命中，查询数据库
        LearningRecord oldRecord = lambdaQuery()
                .eq(LearningRecord::getLessonId, lessonId)
                .eq(LearningRecord::getSectionId, sectionId)
                .one();
        // 4、如果查询到数据，写入缓存
        if (oldRecord != null) {
            delayTaskHandler.writeRecordCache(oldRecord);
        }
        return oldRecord;
    }
}
