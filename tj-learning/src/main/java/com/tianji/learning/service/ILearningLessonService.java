package com.tianji.learning.service;

import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.po.LearningLesson;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.LearningLessonVO;

import java.util.List;

/**
 * <p>
 * 学生课程表 服务类
 * </p>
 *
 * @author lusy
 * @since 2025-03-06
 */
public interface ILearningLessonService extends IService<LearningLesson> {

    /**
     * 添加用户课表
     * @param userId
     * @param courseIds
     */
    void addUserLessons(Long userId, List<Long> courseIds);

    PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery);

    LearningLessonVO queryMyCurrentLesson();

    void deleteUserLessons(Long userId, List<Long> courseIds);

    Long isLessonValid(Long courseId);

    LearningLessonVO queryLessonByCourse(Long courseId);

    Integer countLearningLessonByCourse(Long courseId);
}
