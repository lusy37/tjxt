package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.service.IInteractionReplyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Api(tags = "管理端评论相关接口")
@RequiredArgsConstructor
@RestController
@RequestMapping("/admin/replies")
public class InteractionReplyAdminController {

    private final IInteractionReplyService interactionReplyService;

    @ApiOperation("分页查询回答or评论")
    @GetMapping("/page")
    public PageDTO<ReplyVO> pageReplyAdmin(ReplyPageQuery query) {
        return interactionReplyService.pageReplyAdmin(query);
    }

    @ApiOperation("隐藏显示回答or评论")
    @PutMapping("/{id}/hidden/{hidden}")
    public void hiddenReplyAdmin(@PathVariable Long id, @PathVariable Boolean hidden) {
        interactionReplyService.hiddenReplyAdmin(id, hidden);
    }
}
