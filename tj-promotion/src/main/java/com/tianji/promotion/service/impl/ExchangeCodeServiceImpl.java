package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BooleanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.constants.ExchangeCodeStatus;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.query.CodeQuery;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.ExchangeCodeMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.tianji.promotion.utils.CodeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.tianji.promotion.constants.PromotionErrorInfo.*;

/**
 * <p>
 * 兑换码 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-09-06
 */
@Slf4j
@Service
public class ExchangeCodeServiceImpl extends ServiceImpl<ExchangeCodeMapper, ExchangeCode> implements IExchangeCodeService {

    private final static String COUPON_CODE_SERIAL_KEY = "coupon:code:serial";
    private final static String COUPON_CODE_CHECK_KEY = "coupon:code:map";
    private final CodeUtil codeUtil;
    private final BoundValueOperations<String, String> codeOps;
    private final StringRedisTemplate stringRedisTemplate;
    private final IUserCouponService userCouponService;
    private final CouponMapper couponMapper;

    public ExchangeCodeServiceImpl(StringRedisTemplate stringRedisTemplate, CodeUtil codeUtil,
                                   IUserCouponService userCouponService, CouponMapper couponMapper) {
        this.codeUtil = codeUtil;
        this.codeOps = stringRedisTemplate.boundValueOps(COUPON_CODE_SERIAL_KEY);
        this.stringRedisTemplate = stringRedisTemplate;
        this.userCouponService = userCouponService;
        this.couponMapper = couponMapper;
    }

    @Override
    @Async("generateExchangeCodeExecutor")
    public void generateExchangeCodeAsync(Coupon coupon) {
        Long couponId = coupon.getId();
        Integer totalNum = coupon.getTotalNum();
        LocalDateTime issueEndTime = coupon.getIssueEndTime();
        log.debug("准备给优惠券{}生成兑换码，数量：{}", couponId, totalNum);
        // 1.获取兑换码的序列号，由Redis递增生成
        Long serialNum = codeOps.increment(totalNum);
        if (serialNum == null) {
            serialNum = 1L;
        }
        // 2.准备生成兑换码
        List<ExchangeCode> list = new ArrayList<>(totalNum);
        for (long i = serialNum - totalNum + 1; i <= serialNum; i++) {
            ExchangeCode code = new ExchangeCode();
            code.setId(i);
            code.setExchangeTargetId(couponId);
            code.setCode(codeUtil.generateCode(i));
            code.setExpiredTime(issueEndTime);
            list.add(code);
        }
        // 3.写入数据库
        saveBatch(list);
        log.debug("优惠券{}成功生成{}个兑换码", couponId, totalNum);
    }

    @Override
    public PageDTO<String> queryCodePage(CodeQuery query) {
        // 1.分页查询兑换码
        Page<ExchangeCode> page = lambdaQuery()
                .eq(ExchangeCode::getStatus, query.getStatus())
                .eq(ExchangeCode::getExchangeTargetId, query.getCouponId())
                .page(query.toMpPage());
        // 2.返回数据
        return PageDTO.of(page, ExchangeCode::getCode);
    }

    @Override
    @Transactional
    public void exchangeCoupon(String code) {
        // 1.根据兑换码解析信息，如果不正确此处会抛出异常
        long id = codeUtil.parseCode(code);
        // 2.先校验兑换码是否已经兑换过，此处利用Redis的BitMap即可
        Boolean exists = stringRedisTemplate.opsForValue().getBit(COUPON_CODE_CHECK_KEY, id);
        if (BooleanUtils.isTrue(exists)) {
            // 兑换码已经兑换过了，报错
            throw new BizIllegalException("兑换码已经使用过了");
        }
        // 3.兑换码未使用，查询数据库
        ExchangeCode exchangeCode = getById(id);
        // 4.再次校验兑换码是否一致、兑换码是否已经兑换或过期
        if (!code.equals(exchangeCode.getCode())
                || !ExchangeCodeStatus.UNUSED.equalsValue(exchangeCode.getStatus())) {
            // 兑换码与数据库码不一致、或者已经兑换或过期
            throw new BadRequestException(INVALID_COUPON_CODE);
        }

        // 5.根据兑换码类型，查询对应的物品信息，目前固定是查询优惠券信息
        Coupon coupon = couponMapper.selectById(exchangeCode.getExchangeTargetId());
        if(coupon == null){
            // 5.1.优惠券不存在
            throw new BizIllegalException(COUPON_NOT_EXISTS);
        }
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getIssueEndTime().isBefore(now)) {
            // 5.2.优惠券过了签发日期
            throw new BizIllegalException(COUPON_ISSUE_EXPIRED);
        }
        if (coupon.getIssueNum() >= coupon.getTotalNum()) {
            // 5.3.优惠券库存不足
            throw new BizIllegalException(COUPON_NOT_ENOUGH);
        }
        // 5.4.是否有限领数量
        Integer userLimit = coupon.getUserLimit();
        Long userId = UserContext.getUser();
        if(userLimit != null && userLimit > 0){
            // 有限领数量，查询是否已经领取过
            int userReceiveNum = userCouponService.countUserReceiveNum(coupon.getId(), userId);
            if (userReceiveNum >= userLimit) {
                // 超出领取数量
                throw new BizIllegalException("领取次数过多");
            }
        }

        // 6.生成用户券
        userCouponService.createUserCouponWithId(coupon, IdWorker.getId(), userId);

        // 7.修改兑换码状态
        lambdaUpdate()
                .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED.getValue())
                .set(ExchangeCode::getUserId, userId)
                .eq(ExchangeCode::getId, id)
                .update();

        // 8.减优惠券库存
        couponMapper.minusCouponIssueAmount(coupon.getId());
        // 9.设置BitMap
        stringRedisTemplate.opsForValue().setBit(COUPON_CODE_CHECK_KEY, id, true);
    }

}
