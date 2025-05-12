package com.tianji.remark.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.api.dto.remark.LikedTimesDTO;
import com.tianji.remark.domain.po.LikedStat;

import java.util.List;

public interface ILikedStatService extends IService<LikedStat> {
    void updateLikedTimes(String bizType, List<LikedTimesDTO> msg);
}