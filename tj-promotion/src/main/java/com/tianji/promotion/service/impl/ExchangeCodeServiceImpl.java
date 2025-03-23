package com.tianji.promotion.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.utils.CollUtils;
import com.tianji.promotion.constant.PromotionConstants;
import com.tianji.promotion.domain.pojo.Coupon;
import com.tianji.promotion.domain.pojo.ExchangeCode;
import com.tianji.promotion.domain.query.CodeQuery;
import com.tianji.promotion.domain.vo.ExchangeCodeVO;
import com.tianji.promotion.mapper.ExchangeCodeMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.CodeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.tianji.promotion.constant.PromotionConstants.COUPON_RANGE_KEY;

/**
 * <p>
 * 兑换码 服务实现类
 * </p>
 *
 * @author lusy
 * @since 2025-03-19
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ExchangeCodeServiceImpl extends ServiceImpl<ExchangeCodeMapper, ExchangeCode> implements IExchangeCodeService {

    private final StringRedisTemplate redisTemplate;

    @Override
    @Async("generateExchangeCodeExecutor")
    public void asyncGenerateExchangeCode(Coupon coupon) {

        //生成自增 id: 判断需要生成多少个验证码, 更新 Redis 中的值
        Integer totalNum = coupon.getTotalNum();
        Long increment = redisTemplate.opsForValue().increment(PromotionConstants.COUPON_CODE_SERIAL_KEY, totalNum);
        if (increment == null) {
            return;
        }
        try {
            int maxSerialNum = increment.intValue();
            // 循环生成兑换码
            List<ExchangeCode> list = new ArrayList<>();
            for (int serialNum  = maxSerialNum - totalNum + 1; serialNum  <= maxSerialNum; serialNum ++) {
                // 生成兑换码
                String code = CodeUtil.generateCode(serialNum , coupon.getId());

                // 封装兑换码实例
                ExchangeCode exchangeCode = new ExchangeCode()
                        .setId(serialNum)
                        .setCode(code)
                        .setExchangeTargetId(coupon.getId())
                        .setExpiredTime(coupon.getIssueEndTime());

                // 保存到列表
                list.add(exchangeCode);
            }
            // 保存数据库
            saveBatch(list);

            // 写入Redis缓存，member：couponId，score：兑换码的最大序列号
            redisTemplate.opsForZSet().add(COUPON_RANGE_KEY, coupon.getId().toString(), maxSerialNum);
        } catch (Exception e) {
            redisTemplate.opsForValue().decrement(PromotionConstants.COUPON_CODE_SERIAL_KEY, totalNum);
            throw new RuntimeException(e);
        }
    }

    @Override
    public PageDTO<ExchangeCodeVO> queryExchangeCodeByCouponId(CodeQuery query) {

        Page<ExchangeCode> page = lambdaQuery()
                .eq(ExchangeCode::getExchangeTargetId, query.getCouponId())
                .eq(ExchangeCode::getStatus, query.getStatus())
                .page(query.toMpPage());

        List<ExchangeCode> records = page.getRecords();

        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        List<ExchangeCodeVO> voList = BeanUtil.copyToList(records, ExchangeCodeVO.class);
        return PageDTO.of(page, voList);
    }

    @Override
    public boolean updateExchangeMark(long serialNum, boolean mark) {
        // 这里的 boo 返回的是原来位置的值,如果是false,表示未使用过,true则表示已经兑换过
        Boolean boo = redisTemplate.opsForValue().setBit(PromotionConstants.COUPON_CODE_SERIAL_KEY, serialNum, mark);
        log.info("boo:{}",boo);
        return boo != null && boo;
    }

    @Override
    public Long exchangeTargetId(long serialNum) {
        // 查询score值比当前序列号大的第一个优惠券的值
        Set<String> result = redisTemplate.opsForZSet()
                .rangeByScore(COUPON_RANGE_KEY, serialNum, serialNum + 5000, 0L, 1L);
        if (CollUtils.isEmpty(result)) {
            return null;
        }
        // 数据转换
        String next = result.iterator().next();
        return Long.valueOf(next);
    }
}
