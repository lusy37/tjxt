package com.tianji.learning.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CategoryClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSearchDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.QuestionStatus;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author lusy
 * @since 2025-03-11
 */
@Service
@RequiredArgsConstructor
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion> implements IInteractionQuestionService {

    private final UserClient userClient;
    private final SearchClient searchClient;
    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;
    private final CategoryCache categoryCache;
    private final IInteractionReplyService interactionReplyService;
    

    @Override
    public void saveQuestion(QuestionFormDTO questionFormDTO) {

        // 获取用户 id
        Long userId = UserContext.getUser();

        // 补充数据
        InteractionQuestion interactionQuestion = BeanUtil.copyProperties(questionFormDTO, InteractionQuestion.class);
        interactionQuestion.setUserId(userId);

        // 新增
        save(interactionQuestion);
    }

    @Override
    public void updateQuestion(Integer id, QuestionFormDTO questionFormDTO) {
        // 获取用户 id
        Long userId = UserContext.getUser();

        // 补充数据
        InteractionQuestion interactionQuestion = BeanUtil.copyProperties(questionFormDTO, InteractionQuestion.class);
        interactionQuestion.setUserId(userId);

        // 新增
        updateById(interactionQuestion);
    }

    @Override
    public PageDTO<QuestionVO> pageQuery(QuestionPageQuery questionPageQuery) {

        // 校验课程 id 不能为空
        Long courseId = questionPageQuery.getCourseId();
        Long sectionId = questionPageQuery.getSectionId();
        Boolean onlyMine = questionPageQuery.getOnlyMine();
        if (courseId == null) {
            throw new BadRequestException("课程 id 不能为空");
        }

        // 分页查询问题列表
        Page<InteractionQuestion> page = lambdaQuery()
                // 查询字段, 排除掉 description 大字段
                .select(InteractionQuestion.class, info -> !info.getProperty().equals("description"))
                .eq(InteractionQuestion::getCourseId, courseId)
                .eq(sectionId != null, InteractionQuestion::getSectionId, sectionId)
                .eq(onlyMine, InteractionQuestion::getUserId, UserContext.getUser())
                .eq(InteractionQuestion::getHidden, false)
                .page(questionPageQuery.toMpPageDefaultSortByCreateTimeDesc());

        // 校验 page 是否有值
        List<InteractionQuestion> records = page.getRecords();
        if (CollUtil.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        // 收集回答人的 id
        Set<Long> answerIds = records.stream()
                .map(InteractionQuestion::getLatestAnswerId)
                .collect(Collectors.toSet());
        answerIds.remove(null);

        // 根据 id 查询最近一次回答的人 注意判断是否 匿名、隐藏
        Map<Long, InteractionReply> answerMap = null;
        if (CollUtil.isNotEmpty(answerIds)) {
            List<InteractionReply> replyList = interactionReplyService.lambdaQuery()
                    .in(InteractionReply::getId, answerIds)
                    .eq(InteractionReply::getHidden, false)
                    .list();
            if (CollUtil.isNotEmpty(replyList)) {
                answerMap = replyList.stream()
                        .distinct()
                        .collect(Collectors.toMap(InteractionReply::getUserId, r -> r,(existing, replacement) -> replacement));
            }
        }

        // 收集问题的 userId , 排除掉匿名的
        Set<Long> userIds = records.stream()
                        .filter(r -> !r.getAnonymity())
                        .map(InteractionQuestion::getUserId)
                        .collect(Collectors.toSet());
        // userIds 中加上 answerIds 中不匿名的
        if (CollUtil.isNotEmpty(answerIds) && CollUtil.isNotEmpty(answerMap)) {
            for (Long answerId : answerIds) {
                InteractionReply interactionReply = answerMap.get(answerId);
                if (interactionReply != null && !interactionReply.getAnonymity()) {
                    userIds.add(interactionReply.getUserId());
                }
            }
        }
        // 查询 userIds 的具体信息
        Map<Long, UserDTO> userMap = null;
        if (CollUtil.isNotEmpty(userIds)) {
            List<UserDTO> users = userClient.queryUserByIds(userIds);
            userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
        }


        // 封装数据
        List<QuestionVO> voList = new ArrayList<>();
        for (InteractionQuestion record : records) {
            QuestionVO vo = BeanUtil.copyProperties(record, QuestionVO.class);
            // 处理用户信息
            //vo.setUserId(null);

            // 判断提问是否需要匿名
            if (!vo.getAnonymity() && userMap != null) {
                UserDTO userDTO = userMap.get(record.getUserId());
                if (userDTO != null) {
                    vo.setUserId(userDTO.getId());
                    vo.setUserIcon(userDTO.getIcon());
                    vo.setUserName(userDTO.getName());
                }
            }

            // 补充最近一次回答的信息
            if (vo.getAnswerTimes() != 0 && answerMap != null) {
                InteractionReply interactionReply = answerMap.get(record.getLatestAnswerId());
                if (interactionReply != null) {
                    if (!interactionReply.getAnonymity()) {
                        UserDTO user = userMap.get(interactionReply.getUserId());
                        vo.setLatestReplyUser(user.getName());
                    }
                    vo.setLatestReplyContent(interactionReply.getContent());
                }
            }

            voList.add(vo);
        }

        return PageDTO.of(page, voList);
    }

    @Override
    public QuestionVO getQuestionById(Long id) {

        // 校验 id
        if (id == null) {
            throw new BadRequestException("问题 id 不能为空");
        }

        InteractionQuestion question = getById(id);

        if (question == null || question.getHidden()) {
            return null;
        }

        UserDTO user = null;
        if (!question.getAnonymity()) {
            user = userClient.queryUserById(question.getUserId());
        }

        QuestionVO vo = BeanUtil.copyProperties(question, QuestionVO.class);
        vo.setUserId(null);

        if (user != null) {
            vo.setUserId(user.getId());
            vo.setUserIcon(user.getIcon());
            vo.setUserName(user.getName());
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteQuestionById(Long id) {
        // 校验 id
        if (id == null) {
            throw new BadRequestException("问题 id 不能为空");
        }
        // 当前用户 id
        Long userId = UserContext.getUser();
        // 查询问题
        InteractionQuestion question = getById(id);
        if (question == null || !question.getUserId().equals(userId)) {
            throw new BizIllegalException("问题不存在或者不属于当前用户");
        }

        removeById(id);

        List<InteractionReply> replyList = interactionReplyService.lambdaQuery()
                .select(InteractionReply.class, info -> info.getProperty().equals("id"))
                .eq(InteractionReply::getQuestionId, id)
                .list();

        if (CollUtil.isNotEmpty(replyList)) {
            List<Long> replyIds = replyList.stream().map(InteractionReply::getId).collect(Collectors.toList());
            interactionReplyService.removeByIds(replyIds);
        }

    }

    @Override
    public PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery query) {
        // 1、处理课程名称，得到课程 id
        List<Long> courseIds = null;
        if (StrUtil.isNotBlank(query.getCourseName())) {
            courseIds = searchClient.queryCoursesIdByName(query.getCourseName());
        }

        // 2、分页查询问题列表
        Integer status = query.getStatus();
        LocalDateTime beginTime = query.getBeginTime();
        LocalDateTime endTime = query.getEndTime();

        Page<InteractionQuestion> page = lambdaQuery()
                .in(CollUtil.isNotEmpty(courseIds),InteractionQuestion::getCourseId, courseIds)
                .eq(status != null, InteractionQuestion::getStatus, status)
                .between(beginTime != null && endTime != null, InteractionQuestion::getCreateTime,
                        beginTime, endTime)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());

        List<InteractionQuestion> records = page.getRecords();

        if (CollUtil.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        // 3.准备VO需要的数据：用户数据、课程数据、章节数据
        Set<Long> userIds = new HashSet<>();
        Set<Long> cIds = new HashSet<>();
        Set<Long> cataIds = new HashSet<>();
        // 获取各种数据的id集合
        for (InteractionQuestion q : records) {
            userIds.add(q.getUserId());
            cIds.add(q.getCourseId());
            cataIds.add(q.getChapterId());
            cataIds.add(q.getSectionId());
        }

        // 4、获取问题列表对应的用户信息
        Map<Long, UserDTO> userMap = null;

        List<UserDTO> userDTOList = userClient.queryUserByIds(userIds);

        if (CollUtil.isNotEmpty(userDTOList)) {
            userMap = userDTOList.stream()
                    .collect(Collectors.toMap(UserDTO::getId, u -> u));
        }

        // 5、获取课程信息
        Map<Long, CourseSimpleInfoDTO> courseMap = null;

        List<CourseSimpleInfoDTO> clientSimpleInfoList = courseClient.getSimpleInfoList(cIds);

        if (CollUtil.isNotEmpty(clientSimpleInfoList)) {
            courseMap = clientSimpleInfoList.stream()
                    .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        }

        // 6、根据id查询章节
        Map<Long, String> cataMap = null;

        List<CataSimpleInfoDTO> cataSimpleInfoDTOList = catalogueClient.batchQueryCatalogue(cataIds);

        if (CollUtil.isNotEmpty(cataSimpleInfoDTOList)) {
            cataMap = cataSimpleInfoDTOList.stream()
                    .collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));
        }

        // 封装 vo
        List<QuestionAdminVO> voList = new ArrayList<>();

        for (InteractionQuestion record : records) {

            QuestionAdminVO vo = BeanUtil.copyProperties(record, QuestionAdminVO.class);

            // 用户信息
            UserDTO userDTO = userMap.get(record.getUserId());
            if (userDTO != null) {
                vo.setUserName(userDTO.getName());
            }

            // 课程名称、分类信息
            CourseSimpleInfoDTO courseSimpleInfoDTO = courseMap.get(record.getCourseId());
            if (courseSimpleInfoDTO != null) {
                vo.setCourseName(courseSimpleInfoDTO.getName());
                vo.setCategoryName(categoryCache.getCategoryNames(courseSimpleInfoDTO.getCategoryIds()));
            }

            // 课程章节信息
            vo.setChapterName(cataMap.getOrDefault(record.getChapterId(),""));
            vo.setSectionName(cataMap.getOrDefault(record.getSectionId(),""));

            voList.add(vo);
        }

        return PageDTO.of(page,voList);
    }

    @Override
    public void updateQuestionHiddenStatus(Long id, Boolean hidden) {

        // 校验 id 和 hidden
        if (id == null || hidden == null) {
            throw new BizIllegalException("问题 id 和隐藏状态不能为空");
        }

        InteractionQuestion interactionQuestion = getById(id);

        if (interactionQuestion == null) {
            throw new BizIllegalException("问题不存在");
        }

        interactionQuestion.setHidden(hidden);
        updateById(interactionQuestion);
    }

    @Override
    public QuestionAdminVO getQuestionAdminById(Long id) {

        // 校验id
        if(id == null) {
            throw new BadRequestException("问题 id 不能为空");
        }
        // 查询问题详情
        InteractionQuestion question = getById(id);

        if (ObjectUtil.isEmpty(question)) {
            return null;
        }

        QuestionAdminVO vo = BeanUtil.copyProperties(question, QuestionAdminVO.class);
        // 封装数据
        UserDTO user = userClient.queryUserById(question.getUserId());
        if (user != null) {
            vo.setUserName(user.getName());
            vo.setUserIcon(user.getIcon());
        }

        CourseFullInfoDTO courseFullInfoDTO = courseClient
                .getCourseInfoById(question.getCourseId(), true, true);
        if (courseFullInfoDTO != null) {
            vo.setCourseName(courseFullInfoDTO.getName());
            vo.setCategoryName(categoryCache.getCategoryNames(courseFullInfoDTO.getCategoryIds()));

            UserDTO teacher = userClient.queryUserById(courseFullInfoDTO.getTeacherIds().get(0));
            vo.setTeacherName(teacher.getName());
        }

        Map<Long, String> cataMap = catalogueClient.batchQueryCatalogue(
                Arrays.asList(question.getChapterId(), question.getSectionId()))
                .stream()
                .collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));
        vo.setChapterName(cataMap.getOrDefault(question.getChapterId(),""));
        vo.setSectionName(cataMap.getOrDefault(question.getSectionId(),""));

        // 修改问题查看状态
        if (QuestionStatus.UN_CHECK.equals(question.getStatus())) {
            question.setStatus(QuestionStatus.CHECKED);
            updateById(question);
        }

        return vo;
    }
}
