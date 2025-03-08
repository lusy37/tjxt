package com.tianji.learning.controller;


import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordFormDTO;
import com.tianji.learning.service.ILearningRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 学习记录表 前端控制器
 * </p>
 *
 * @author lusy
 * @since 2025-03-08
 */
@Api(tags = "学习记录相关接口")
@RequiredArgsConstructor
@RestController
@RequestMapping("/learning-records")
public class LearningRecordController {

    private final ILearningRecordService learningRecordService;

    /**
     * 查询当前用户指定课程的学习进度
     * @param courseId 课程id
     * @return 课表信息、学习记录及进度信息
     */
    @ApiOperation("查询指定课程的学习记录")
    @GetMapping("/course/{courseId}")
    LearningLessonDTO queryLearningRecordByCourse(@PathVariable("courseId") Long courseId) {

        return learningRecordService.queryLearningRecordByCourse(courseId);
    }

    @ApiOperation("提交学习记录")
    @PostMapping()
    void addLearningRecord(@RequestBody LearningRecordFormDTO learningRecordFormDTO) {

        learningRecordService.addLearningRecord(learningRecordFormDTO);
    }

}
