package com.tianji.learning.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.remark.RemarkClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionReplyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>
 * 互动问题的回答或评论 服务实现类
 * </p>
 *
 * @author lusy
 * @since 2025-03-11
 */
@Service
@RequiredArgsConstructor
public class InteractionReplyServiceImpl extends ServiceImpl<InteractionReplyMapper, InteractionReply> implements IInteractionReplyService {

    private final InteractionQuestionMapper questionMapper;
    private final UserClient userClient;
    private final RemarkClient remarkClient;

    @Override
    @Transactional
    public void saveReply(ReplyDTO replyDTO) {

        // 转换为实体类
        InteractionReply reply = BeanUtil.copyProperties(replyDTO, InteractionReply.class);
        Long userId = UserContext.getUser();
        reply.setUserId(userId);
        // 写入数据库
        save(reply);


        // 判断是回答还是评论
        if (reply.getAnswerId() == null) {
            // 回答
            // 更新回答的评论数量
            questionMapper.update(null,
                    new UpdateWrapper<InteractionQuestion>()
                            .setSql("answer_times = answer_times + 1")
                            .set("latest_answer_id", reply.getId())
                            .set(replyDTO.getIsStudent(),"status", 0)
                            .eq("id", replyDTO.getQuestionId()));

        } else {
            // 评论

            // 拿到相关的回答
            InteractionReply answer = getById(reply.getAnswerId());
            // 更新评论的评论数量
            update(answer, new UpdateWrapper<InteractionReply>()
                            .setSql("reply_times = reply_times + 1")
                            .eq("id", reply.getAnswerId()));

            if (replyDTO.getIsStudent()) {
                questionMapper.update(null, new UpdateWrapper<InteractionQuestion>()
                        .set("status", 0)
                        .eq("id", replyDTO.getQuestionId()));
            }
        }
    }

    @Override
    public PageDTO<ReplyVO> pageReply(ReplyPageQuery query) {

        // 校验 questionId 和 answerId 不能同时为空
        Long questionId = query.getQuestionId();
        Long answerId = query.getAnswerId();
        if (questionId == null && answerId == null) {
            throw new BizIllegalException("questionId 和 answerId 不能同时为空");
        }

        if (answerId == null) {
            return getReplyVOPage(query, questionId, null, false);
        }else {
            return getReplyVOPage(query,null, answerId, false);
        }

        // 查询评论
    }

    @Override
    public PageDTO<ReplyVO> pageReplyAdmin(ReplyPageQuery query) {
        // 校验 questionId 和 answerId 不能同时为空
        Long questionId = query.getQuestionId();
        Long answerId = query.getAnswerId();
        if (questionId == null && answerId == null) {
            throw new BizIllegalException("questionId 和 answerId 不能同时为空");
        }

        if (answerId == null) {
            return getReplyVOPage(query, questionId, null, true);
        }else {
            return getReplyVOPage(query,null, answerId, true);
        }

        // 查询评论
    }

    @Override
    @Transactional
    public void hiddenReplyAdmin(Long id, Boolean hidden) {

        //校验 id 和 hidden 是否为空
        if (ObjectUtil.isEmpty(id) || ObjectUtil.isEmpty(hidden)) {
            throw new BizIllegalException("id 和 hidden 不能为空");
        }

        // 根据 id 查询回复信息
        InteractionReply reply = getById(id);

        if (ObjectUtil.isEmpty(reply)) {
            throw new BizIllegalException("回复信息不存在");
        }

        // 更新回复信息
        reply.setHidden(hidden);
        updateById(reply);
        // 更新子信息的状态
        if (hidden) {
            lambdaUpdate()
                    .set(InteractionReply::getHidden, true)
                    .eq(InteractionReply::getAnswerId, reply.getId())
                    .update();
        }
    }

    private PageDTO<ReplyVO> getReplyVOPage(ReplyPageQuery query, Long questionId, Long answerId, Boolean isAdmin) {
        // 查询回答
        Page<InteractionReply> page = lambdaQuery()
                .eq(questionId != null, InteractionReply::getQuestionId, questionId)
                .eq(InteractionReply::getAnswerId,answerId != null ? answerId : 0) // 如果查询的是一级评论，answerId 传入 0
                .eq(!isAdmin,InteractionReply::getHidden, false)
                .page(query.toMpPage("liked_times", false));

        List<InteractionReply> records = page.getRecords();

        if(CollUtil.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        // 获取所有评论的 id, 查看用户点赞状态
        List<Long> bizIds = records.stream()
                .map(InteractionReply::getId)
                .collect(Collectors.toList());
        Set<Long> bizLiked = remarkClient.isBizLiked(bizIds);

        Set<Long> userIds = records.stream()
                .flatMap(reply -> Stream.of(reply.getUserId(), reply.getTargetUserId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());


        // 查询用户信息
        Map<Long, UserDTO> userMap = null;
        List<UserDTO> userDTOList = userClient.queryUserByIds(userIds);
        if (CollUtil.isNotEmpty(userDTOList)) {
            userMap = userDTOList.stream()
                    .collect(Collectors.toMap(UserDTO::getId, u -> u));
        }

        // 封装数据
        List<ReplyVO> voList = new ArrayList<>();

        for (InteractionReply record : records) {
            ReplyVO vo = BeanUtil.copyProperties(record, ReplyVO.class);

            if (!record.getAnonymity() || isAdmin) {
                UserDTO userDTO = userMap.get(record.getUserId());
                if (userDTO != null) {
                    vo.setUserName(userDTO.getName());
                    vo.setUserIcon(userDTO.getIcon());
                }
            }

            if (record.getTargetUserId() != 0) {
                UserDTO userDTO = userMap.get(record.getTargetUserId());
                InteractionReply answer = getById(record.getTargetReplyId());
                if (userDTO != null && answer != null && (!answer.getAnonymity() || isAdmin)) {
                    // 判断用户是否存在, 这条评论是否存在, 并且目标用户是否匿名
                    vo.setTargetUserName(userDTO.getName());
                }
            }

            // 封装当前用户是否点过赞
            if (CollUtil.isNotEmpty(bizLiked)) {
                vo.setLiked(bizLiked.contains(record.getId()));
            }

            voList.add(vo);
        }

        return PageDTO.of(page, voList);
    }
}
