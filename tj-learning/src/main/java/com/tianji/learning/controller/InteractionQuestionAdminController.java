package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Api(tags = "管理端-互动提问问题管理")
@RequiredArgsConstructor
@RestController()
@RequestMapping("/admin/questions")
public class InteractionQuestionAdminController {

    private final IInteractionQuestionService interactionQuestionService;

    @ApiOperation("分页查询问题")
    @GetMapping("/page")
    public PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery query) {
        return interactionQuestionService.queryQuestionPageAdmin(query);
    }

    @ApiOperation("修改问题的显示隐藏状态")
    @PutMapping("/{id}/hidden/{hidden}")
    public void updateQuestionHiddenStatus(@PathVariable Long id,@PathVariable Boolean hidden) {
        interactionQuestionService.updateQuestionHiddenStatus(id, hidden);
    }

    @ApiOperation("根据id查询问题详情")
    @GetMapping("/{id}")
    public QuestionAdminVO getQuestionAdminById(@PathVariable Long id) {
        return interactionQuestionService.getQuestionAdminById(id);
    }


}
