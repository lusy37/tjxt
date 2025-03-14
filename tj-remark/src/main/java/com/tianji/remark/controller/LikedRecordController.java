package com.tianji.remark.controller;


import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.service.ILikedRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * 点赞记录表 前端控制器
 * </p>
 *
 * @author lusy
 * @since 2025-03-13
 */
@Api(tags = "点赞记录表")
@RequiredArgsConstructor
@RestController
@RequestMapping("/likes")
public class LikedRecordController {

    private final ILikedRecordService likedRecordService;

    @ApiOperation("点赞or取消点赞")
    @PostMapping
    public void addLikeRecord(@Valid @RequestBody LikeRecordFormDTO recordFormDTO) {

        likedRecordService.addLikeRecord(recordFormDTO);
    }


    @GetMapping("list")
    @ApiOperation("查询指定业务id的点赞状态")
    public Set<Long> isBizLiked(@RequestParam("bizIds") List<Long> bizIds) {
        return likedRecordService.isBizLiked(bizIds);
    }
}
