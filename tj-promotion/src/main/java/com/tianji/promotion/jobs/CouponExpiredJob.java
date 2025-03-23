package com.tianji.promotion.jobs;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.utils.CollUtils;
import com.tianji.promotion.domain.pojo.UserCoupon;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.service.IUserCouponService;
import com.tianji.promotion.utils.ToEmail;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class CouponExpiredJob {

    private final JavaMailSender mailSender;
    private final IUserCouponService userCouponService;

    @Value("${spring.mail.username}")
    private String from;

    @XxlJob("couponExpiredJobHandler")
    public void couponExpiredJobHandler() {
        log.info("优惠券过期通知定时任务开始执行");

        // 获取分片信息
        int index = XxlJobHelper.getShardIndex() + 1;
        int size = Integer.parseInt(XxlJobHelper.getJobParam());
        // 分片扫描数据库中还有不到一天过期的优惠券
        Page<UserCoupon> page = userCouponService.lambdaQuery()
                .eq(UserCoupon::getStatus, UserCouponStatus.UNUSED)
                .le(UserCoupon::getTermEndTime, LocalDateTime.now().plusDays(1))
                .page(Page.of(index, size));

        List<UserCoupon> list = page.getRecords();
        if (CollUtils.isEmpty(list)) {
            return;
        }
        String[] strings = new String[1];
        strings[0] = "luziqiang333@gmail.com";
        for (UserCoupon userCoupon : list) {
            ToEmail toEmail = ToEmail.builder()
                    .tos(strings)
                    .subject("优惠券即将过期通知")
                    .content("您的优惠券" + userCoupon.getCouponId() + "即将过期，请及时使用。")
                    .build();

            //创建简单邮件消息
            SimpleMailMessage message = new SimpleMailMessage();
            //谁发的
            message.setFrom(from);
            //谁要接收
            message.setTo(toEmail.getTos());
            //邮件标题
            message.setSubject(toEmail.getSubject());
            //邮件内容
            message.setText(toEmail.getContent());
            try {
                mailSender.send(message);
            } catch (MailException e) {
                e.printStackTrace();
            }
        }
    }
}
