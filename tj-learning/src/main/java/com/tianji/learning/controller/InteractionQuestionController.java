package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author lusy
 * @since 2025-03-11
 */
@Api(tags = "互动问题相关接口")
@RestController
@RequestMapping("/questions")
@RequiredArgsConstructor
public class InteractionQuestionController {

    private final IInteractionQuestionService interactionQuestionService;

    @ApiOperation("新增问题")
    @PostMapping
    public void saveQuestion(@RequestBody QuestionFormDTO questionFormDTO) {
        interactionQuestionService.saveQuestion(questionFormDTO);
    }

    @ApiOperation("修改问题")
    @PutMapping("/{id}")
    public void updateQuestion(@PathVariable Integer id, @RequestBody QuestionFormDTO questionFormDTO) {
        interactionQuestionService.updateQuestion(id, questionFormDTO);
    }

    @ApiOperation("分页查询问题")
    @GetMapping("/page")
    public PageDTO<QuestionVO> pageQuery(QuestionPageQuery questionPageQuery) {
        return interactionQuestionService.pageQuery(questionPageQuery);
    }

    @ApiOperation("根据id查询问题详情")
    @GetMapping("/{id}")
    public QuestionVO getQuestion(@PathVariable Long id) {
        return interactionQuestionService.getQuestionById(id);
    }

    @ApiOperation("根据id删除问题")
    @DeleteMapping("/{id}")
    public void deleteQuestion(@ApiParam(value = "问题id", example = "1") @PathVariable("id") Long id) {
        interactionQuestionService.deleteQuestionById(id);
    }
}
