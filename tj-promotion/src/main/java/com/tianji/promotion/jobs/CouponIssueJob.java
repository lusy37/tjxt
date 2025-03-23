package com.tianji.promotion.jobs;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.utils.CollUtils;
import com.tianji.promotion.domain.pojo.Coupon;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.service.ICouponService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class CouponIssueJob {

    private final ICouponService couponService;

    @XxlJob("couponIssueJobHandler")
    public void couponIssueJobHandler() {
        // 1. 获取分片信息,作为分页,每页最多获取 20 条数据
        int index = XxlJobHelper.getShardIndex() + 1;
        // 从xxl-job的参数中获取每页大小
        int size = Integer.parseInt(XxlJobHelper.getJobParam());
        log.info("[定时更新优惠券状态]分片信息：index={},size={}", index, size);
        // 查询未开始的优惠券
        Page<Coupon> page = couponService.lambdaQuery()
                .eq(Coupon::getStatus, CouponStatus.UN_ISSUE)
                .le(Coupon::getIssueBeginTime, LocalDateTime.now())
                .page(Page.of(index, size));
        // 发放优惠券
        List<Coupon> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return;
        }
        // 批量发放优惠券
        couponService.beginIssueBatch(records);
    }

    @XxlJob("stopIssueJobHandler")
    public void stopIssueJobHandler() {
        // 1. 获取分片信息,作为分页,每页最多获取 20 条数据
        int index = XxlJobHelper.getShardIndex() + 1;
        // 从xxl-job的参数中获取每页大小
        int size = Integer.parseInt(XxlJobHelper.getJobParam());
        log.info("[定时更新优惠券状态]分片信息：index={},size={}", index, size);
        // 查询发放时间结束的优惠券
        Page<Coupon> page = couponService.lambdaQuery()
                .eq(Coupon::getStatus, CouponStatus.ISSUING)
                .le(Coupon::getIssueEndTime, LocalDateTime.now())
                .page(Page.of(index, size));
        // 发放优惠券
        List<Coupon> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return;
        }
        // 批量停止优惠券
        couponService.pauseIssueBatch(records);
    }

}
