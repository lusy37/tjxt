package com.tianji.learning.controller;

import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.service.ISignRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Api(tags = "签到相关接口")
@RequiredArgsConstructor
@RestController
@RequestMapping("/sign-records")
public class SignRecordController {

    private final ISignRecordService signRecordService;

    @ApiOperation("签到功能")
    @PostMapping
    public SignResultVO addSignRecords() {

        return signRecordService.addSignRecords();
    }

    @ApiOperation("获取签到记录")
    @GetMapping
    public List<Byte> getSignRecords() {
        return signRecordService.getSignRecords();
    }

}
