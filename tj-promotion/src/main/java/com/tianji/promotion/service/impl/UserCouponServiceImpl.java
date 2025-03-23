// package com.tianji.promotion.service.impl;
//
// import cn.hutool.core.bean.BeanUtil;
// import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
// import com.tianji.common.domain.dto.PageDTO;
// import com.tianji.common.exceptions.BadRequestException;
// import com.tianji.common.exceptions.BizIllegalException;
// import com.tianji.common.utils.CollUtils;
// import com.tianji.common.utils.UserContext;
// import com.tianji.promotion.domain.pojo.Coupon;
// import com.tianji.promotion.domain.pojo.ExchangeCode;
// import com.tianji.promotion.domain.pojo.UserCoupon;
// import com.tianji.promotion.domain.query.UserCouponQuery;
// import com.tianji.promotion.domain.vo.CouponPageVO;
// import com.tianji.promotion.enums.CouponStatus;
// import com.tianji.promotion.enums.ExchangeCodeStatus;
// import com.tianji.promotion.mapper.CouponMapper;
// import com.tianji.promotion.mapper.UserCouponMapper;
// import com.tianji.promotion.service.IExchangeCodeService;
// import com.tianji.promotion.service.IUserCouponService;
// import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
// import com.tianji.promotion.utils.CodeUtil;
// import com.tianji.promotion.utils.MyLock;
// import com.tianji.promotion.utils.MyLockStrategy;
// import com.tianji.promotion.utils.MyLockType;
// import lombok.RequiredArgsConstructor;
// import org.aspectj.weaver.ast.Var;
// import org.redisson.api.RLock;
// import org.redisson.api.RedissonClient;
// import org.springframework.aop.framework.AopContext;
// import org.springframework.stereotype.Service;
// import org.springframework.transaction.annotation.Transactional;
//
// import java.time.LocalDateTime;
// import java.util.ArrayList;
// import java.util.List;
// import java.util.Map;
// import java.util.Set;
// import java.util.stream.Collectors;
//
// /**
//  * <p>
//  * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
//  * </p>
//  *
//  * @author lusy
//  * @since 2025-03-20
//  */
// @Service
// @RequiredArgsConstructor
// public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {
//
//     private final CouponMapper couponMapper;
//     private final IExchangeCodeService codeService;
//     private final RedissonClient redissonClient;
//     @Override
//     // @Transactional
//     public void receiveCoupon(Long id) {
//         // 获取当前用户 id
//         Long userId = UserContext.getUser();
//         // 获取优惠券信息
//         Coupon coupon = couponMapper.selectById(id);
//         // 校验优惠券是否存在
//         if (coupon == null) {
//             throw new BadRequestException("优惠券不存在");
//         }
//         // 校验是否处于发放状态
//         LocalDateTime now = LocalDateTime.now();
//         if (now.isBefore(coupon.getIssueBeginTime()) || now.isAfter(coupon.getIssueEndTime())) {
//             throw new BadRequestException("优惠券发放已经结束或尚未开始");
//         }
//         // 校验库存是否充足
//         if (coupon.getIssueNum() >= coupon.getTotalNum()) {
//             throw new BadRequestException("优惠券库存不足");
//         }
//         /*        // 创建锁对象
//                 String key = "lock:coupon:uid" + userId;
//                 RLock lock = redissonClient.getLock(key);
//                 // 尝试获取锁
//                 boolean isLock = lock.tryLock();
//                 if (!isLock) {
//                     throw new BizIllegalException("请求太频繁");
//                 }
//                 try {
//                     IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
//                     userCouponService.checkAndCreateUserCoupon(userId, coupon, null);
//                     // checkAndCreateUserCoupon(userId, coupon, null);
//                 } finally {
//                     lock.unlock();
//                 }*/
//         IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
//         userCouponService.checkAndCreateUserCoupon(userId, coupon, null);
//     }
//
//     @Override
//     public void exchangeCoupon(String code) {
//         // 解析兑换码, 得到兑换码对应的 id
//         long serialNum = CodeUtil.parseCode(code);
//         // 判断兑换码是否已经使用过
//         boolean exchanged = codeService.updateExchangeMark(serialNum, true);
//         if (exchanged) {
//             throw new BadRequestException("兑换码已经使用过");
//         }
//         try {
//             // 判断兑换码是否存在
//             ExchangeCode exchangeCode = codeService.getById(serialNum);
//             if (exchangeCode == null) {
//                 throw new BadRequestException("兑换码不存在");
//             }
//             // 判断兑换码是否已经过期
//             if (LocalDateTime.MIN.isAfter(exchangeCode.getExpiredTime())) {
//                 throw new BadRequestException("兑换码已经过期");
//             }
//             // 判断是否超出限领数量
//             Long userId = UserContext.getUser();
//             Coupon coupon = couponMapper.selectById(exchangeCode.getExchangeTargetId());
//             /*            // 创建锁对象
//                         String key = "lock:coupon:uid" + userId;
//                         RLock lock = redissonClient.getLock(key);
//                         // 尝试获取锁
//                         boolean isLock = lock.tryLock();
//                         if (!isLock) {
//                             throw new BizIllegalException("请求太频繁");
//                         }
//                         try {
//                             IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
//                             userCouponService.checkAndCreateUserCoupon(userId, coupon, serialNum);
//                             // checkAndCreateUserCoupon(userId, coupon, null);
//                         } finally {
//                             lock.unlock();
//                         }*/
//             IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
//             userCouponService.checkAndCreateUserCoupon(userId, coupon, serialNum);
//         } catch (Exception e) {
//             // 回滚 redis 操作
//             codeService.updateExchangeMark(serialNum, false);
//             throw e;
//         }
//     }
//
//
//     @Transactional
//     @MyLock(name = "lock:coupon:uid:#{userId}")
//     public void checkAndCreateUserCoupon(Long userId, Coupon coupon, Long serialNum) {
//         // 校验是否达到限领数量
//         Integer count = lambdaQuery()
//                 .eq(UserCoupon::getUserId, userId)
//                 .eq(UserCoupon::getCouponId, coupon.getId())
//                 .count();
//         if (count >= coupon.getUserLimit()) {
//             throw new BadRequestException("优惠券已经达到限领数量");
//         }
//
//         // 5.更新优惠券的已经发放的数量 + 1
//         int r = couponMapper.incrIssueNum(coupon.getId());
//         if (r != 1) {
//             throw new BadRequestException("优惠券发放失败");
//         }
//         // 6.新增一个用户券
//         saveUserCoupon(coupon, userId);
//
//         // 更新兑换码状态
//         if (serialNum != null) {
//             codeService.lambdaUpdate()
//                     .set(ExchangeCode::getUserId, userId)
//                     .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
//                     .eq(ExchangeCode::getId, serialNum)
//                     .update();
//         }
//     }
//
//     @Override
//     public PageDTO<CouponPageVO> queryMyCoupons(UserCouponQuery query) {
//
//         Page<UserCoupon> page = lambdaQuery()
//                 .eq(UserCoupon::getUserId, UserContext.getUser())
//                 .eq(UserCoupon::getStatus, query.getStatus())
//                 .page(query.toMpPage("term_end_time", true));
//         List<UserCoupon> records = page.getRecords();
//         if (CollUtils.isEmpty(records)) {
//             return PageDTO.empty(page);
//         }
//         // 转化成VO
//         Set<Long> couponIds = records.stream().map(UserCoupon::getCouponId).collect(Collectors.toSet());
//         Map<Long, Coupon> couponsMap = null;
//         List<Coupon> coupons = couponMapper.selectBatchIds(couponIds);
//         if (CollUtils.isNotEmpty(coupons)) {
//             couponsMap = coupons.stream().collect(Collectors.toMap(Coupon::getId, c -> c));
//         }
//         List<CouponPageVO> voList = new ArrayList<>();
//         for (UserCoupon record : records) {
//             Coupon coupon = couponsMap.get(record.getCouponId());
//             CouponPageVO vo = BeanUtil.copyProperties(coupon, CouponPageVO.class);
//             voList.add(vo);
//         }
//         return PageDTO.of(page, voList);
//     }
//
//     private void saveUserCoupon(Coupon coupon, Long userId) {
//         // 1.基本信息
//         UserCoupon uc = new UserCoupon();
//         uc.setUserId(userId);
//         uc.setCouponId(coupon.getId());
//         // 2.有效期信息
//         LocalDateTime termBeginTime = coupon.getTermBeginTime();
//         LocalDateTime termEndTime = coupon.getTermEndTime();
//         if (termBeginTime == null) {
//             termBeginTime = LocalDateTime.now();
//             termEndTime = termBeginTime.plusDays(coupon.getTermDays());
//         }
//         uc.setTermBeginTime(termBeginTime);
//         uc.setTermEndTime(termEndTime);
//         // 3.保存
//         save(uc);
//     }
// }
