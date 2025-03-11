package com.tianji.learning.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.unit.DataUnit;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.IdAndNumDTO;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.LessonStatus;
import com.tianji.learning.constants.PlanStatus;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author lusy
 * @since 2025-03-06
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {

    private final CourseClient courseClient;

    private final CatalogueClient catalogueClient;

    private final LearningRecordMapper learningRecordMapper;

    /**
     * 添加用户课表
     *
     * @param userId
     * @param courseIds
     */
    @Override
    public void addUserLessons(Long userId, List<Long> courseIds) {

        // 根据 courseIds 批量查询 课程简单信息
        List<CourseSimpleInfoDTO> clientSimpleInfoList = courseClient.getSimpleInfoList(courseIds);
        // 健壮性判断，如果查询结果为空，直接返回
        if (CollUtil.isEmpty(clientSimpleInfoList)) {
            // 课程不存在，无法添加
            log.error("课程信息不存在，无法添加到课表");
            return;
        }
        // 遍历课程信息，添加到课表中
        List<LearningLesson> lessonList = new ArrayList<>();
        for (CourseSimpleInfoDTO simpleInfoDTO : clientSimpleInfoList) {
            LearningLesson lesson = new LearningLesson();
            // 填充userId和courseId
            lesson.setUserId(userId);
            lesson.setCourseId(simpleInfoDTO.getId());

            // 获取过期时间
            Integer validDuration = simpleInfoDTO.getValidDuration();
            if (validDuration != null && validDuration > 0) {
                // 计算过期时间
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime expireTime = now.plusMonths(validDuration);
                lesson.setCreateTime(now);
                lesson.setExpireTime(expireTime);
            }

            lessonList.add(lesson);
        }

        // 批量保存
        saveBatch(lessonList);
    }

    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery) {

        // 获取用户id
        Long userId = UserContext.getUser();

        // 构建查询语句, 条件：userId
        Page<LearningLesson> lessonPage = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .page(pageQuery.toMpPage("latest_learn_time", false));

        List<LearningLesson> records = lessonPage.getRecords();
        if (records == null || records.isEmpty()) {
            return PageDTO.empty(lessonPage);
        }
        Map<Long, CourseSimpleInfoDTO> cInfoDTOMap = queryCourseSimpleInfoList(records);
        // 分装 vo 数据
        List<LearningLessonVO> list = new ArrayList<>();

        for (LearningLesson record : records) {
            LearningLessonVO lessonVO = BeanUtil.copyProperties(record, LearningLessonVO.class);
            CourseSimpleInfoDTO simpleInfoDTO = cInfoDTOMap.get(record.getCourseId());
            lessonVO.setCourseName(simpleInfoDTO.getName());
            lessonVO.setCourseCoverUrl(simpleInfoDTO.getCoverUrl());
            lessonVO.setSections(simpleInfoDTO.getSectionNum());
            list.add(lessonVO);
        }

        return PageDTO.of(lessonPage, list);
    }

    @Override
    public LearningLessonVO queryMyCurrentLesson() {

        // 拿到当前用户的 id
        Long userId = UserContext.getUser();

        // 建立查询语句，条件 userId、status 为 1
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING)
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("limit 1")
                .one();

        // 如果查询结果为空，返回空
        if (lesson == null) {
            return null;
        }


        LearningLessonVO lessonVO = BeanUtil.copyProperties(lesson, LearningLessonVO.class);

        // 查看课程信息
        CourseFullInfoDTO courseInfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if (courseInfo == null) {
            throw new BadRequestException("课程不存在");
        }
        lessonVO.setCourseName(courseInfo.getName());
        lessonVO.setCourseCoverUrl(courseInfo.getCoverUrl());
        lessonVO.setSections(courseInfo.getSectionNum());

        // 统计课表中的课程总数
        int count = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .count();

        lessonVO.setCourseAmount(count);

        // 查看目录信息
        List<CataSimpleInfoDTO> cataSimpleInfoDTOS = catalogueClient.batchQueryCatalogue(
                Collections.singletonList(lesson.getLatestSectionId()));

        if (cataSimpleInfoDTOS == null || cataSimpleInfoDTOS.isEmpty()) {
            throw new BizIllegalException("目录信息不存在");
        }

        CataSimpleInfoDTO cataInfo = cataSimpleInfoDTOS.get(0);
        lessonVO.setLatestSectionName(cataInfo.getName());
        lessonVO.setLatestSectionIndex(cataInfo.getCIndex());

        return lessonVO;
    }

    @Override
    public void deleteUserLessons(Long userId, List<Long> courseIds) {
        // 删除课程
        lambdaUpdate()
                .eq(LearningLesson::getUserId, userId)
                .in(LearningLesson::getCourseId, courseIds)
                .remove();
    }

    @Override
    public Long isLessonValid(Long courseId) {

        // 获取用户 id
        Long user = UserContext.getUser();

        // 查看用户是否有这门课程的权限
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, user)
                .eq(LearningLesson::getCourseId, courseId)
                .one();

        if (lesson == null || lesson.getExpireTime() == null) {
            return null;
        }

        // 查看用户的课程有效期
        if (lesson.getStatus() == LessonStatus.EXPIRED || lesson.getExpireTime().isBefore(LocalDateTime.now())) {
            // 异步任务更新状态为 EXPIRED
            CompletableFuture.runAsync(() -> {
                if (lesson.getStatus() != LessonStatus.EXPIRED) {
                    lesson.setStatus(LessonStatus.EXPIRED);
                    updateById(lesson);
                }
            });
            return null;
        }

        return lesson.getId();
    }

    @Override
    public LearningLessonVO queryLessonByCourse(Long courseId) {

        // 获取用户 id
        Long userId = UserContext.getUser();

        // 查询课程
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();

        return lesson == null ? null : BeanUtil.copyProperties(lesson, LearningLessonVO.class);
    }

    @Override
    public Integer countLearningLessonByCourse(Long courseId) {
        return lambdaQuery()
                .eq(LearningLesson::getCourseId, courseId)
                .in(LearningLesson::getStatus,
                        LessonStatus.NOT_BEGIN,
                        LessonStatus.LEARNING,
                        LessonStatus.FINISHED)
                .count();
    }

    @Override
    public LearningLesson queryByUserAndCourseId(Long userId, Long courseId) {

        return lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
    }

    @Override
    public void createLearningPlan(Long courseId, Integer freq) {

        Long userId = UserContext.getUser();

        LearningLesson lesson = queryByUserAndCourseId(userId, courseId);

        if (lesson == null) {
            throw new BadRequestException("课程不存在");
        }

        LearningLesson l = new LearningLesson();

        l.setId(lesson.getId());
        l.setWeekFreq(freq);
        if (lesson.getPlanStatus() == PlanStatus.NO_PLAN) {
            l.setPlanStatus(PlanStatus.PLAN_RUNNING);
        }

        updateById(l);
    }

    @Override
    public LearningPlanPageVO queryMyPlans(PageQuery pageQuery) {

        LearningPlanPageVO result = new LearningPlanPageVO();

        // 获取用户 id
        Long userId = UserContext.getUser();

        // 获取本周起始和结束时间
        LocalDate now = LocalDate.now();
        LocalDateTime beginTime = DateUtils.getWeekBeginTime(now);
        LocalDateTime endTime = DateUtils.getWeekEndTime(now);
        // 本周实际学习小节数量
        Integer weekFinished = learningRecordMapper.selectCount(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getFinished, true)
                .gt(LearningRecord::getFinished, beginTime)
                .lt(LearningRecord::getFinished, endTime));
        result.setWeekFinished(weekFinished);

        // 本周总的计划学习小节数量
        QueryWrapper<LearningLesson> wrapper = new QueryWrapper<LearningLesson>()
                .select("sum(week_freq) as totalPlan")
                .eq("user_id", userId)
                .in("status", LessonStatus.NOT_BEGIN, LessonStatus.LEARNING)
                .eq("plan_status", PlanStatus.PLAN_RUNNING);

        Map<String, Object> map = getMap(wrapper);

        int totalPlan = 0;

        if (map != null && map.get("totalPlan") != null) {
            totalPlan = Integer.parseInt(map.get("totalPlan").toString());
        }

        result.setWeekTotalPlan(totalPlan);

        // TODO 3.3.本周学习积分

        // 4.查询分页数据
        // 4.1.分页查询课表信息以及学习计划信息

        Page<LearningLesson> page = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .in(LearningLesson::getStatus, LessonStatus.NOT_BEGIN, LessonStatus.LEARNING)
                .page(pageQuery.toMpPage("latest_learn_time", false));

        List<LearningLesson> records = page.getRecords();
        // 判断是否为空值
        if (CollUtil.isEmpty(records)) {
            LearningPlanPageVO vo = new LearningPlanPageVO();
            vo.setTotal(0L);
            vo.setPages(0L);
            vo.setList(CollUtil.newArrayList());
            return vo;
        }

        // 4.2.查询课表对应的课程信息
        Map<Long, CourseSimpleInfoDTO> cMap = queryCourseSimpleInfoList(records);
        // 4.3.统计每一个课程本周已学习小节数量
        List<IdAndNumDTO> list = learningRecordMapper.countLearnedSections(userId, beginTime, endTime);
        Map<Long, Integer> learnedMap = IdAndNumDTO.toMap(list);
        // 封装数据vo
        ArrayList<LearningPlanVO> voList = new ArrayList<>();
        for (LearningLesson record : records) {
            LearningPlanVO vo = BeanUtil.copyProperties(record, LearningPlanVO.class);
            CourseSimpleInfoDTO simpleInfoDTO = cMap.get(record.getCourseId());
            if (simpleInfoDTO != null) {
                vo.setCourseName(simpleInfoDTO.getName());
                vo.setSections(simpleInfoDTO.getSectionNum());
            }
            vo.setWeekLearnedSections(learnedMap.getOrDefault(record.getId(), 0));
            voList.add(vo);
        }
        return result.pageInfo(page.getTotal(), page.getPages(), voList);
    }


    private Map<Long, CourseSimpleInfoDTO> queryCourseSimpleInfoList(List<LearningLesson> records) {
        // 获取课程 id 集合
        List<Long> courseIds = records.stream()
                .map(LearningLesson::getCourseId)
                .collect(Collectors.toList());

        // 根据课程 id 集合查询课程简单信息
        List<CourseSimpleInfoDTO> courseSimpleInfoDTOList = courseClient.getSimpleInfoList(courseIds);

        if (CollUtils.isEmpty(courseSimpleInfoDTOList)) {
            // 课程不存在，无法添加
            throw new BadRequestException("课程信息不存在！");
        }

        Map<Long, CourseSimpleInfoDTO> cInfoDTOMap = courseSimpleInfoDTOList.stream()
                .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        return cInfoDTOMap;
    }
}
