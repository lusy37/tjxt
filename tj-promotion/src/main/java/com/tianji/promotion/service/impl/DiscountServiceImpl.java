package com.tianji.promotion.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCouponDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.pojo.Coupon;
import com.tianji.promotion.domain.pojo.CouponScope;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.IDiscountService;
import com.tianji.promotion.strategy.discount.Discount;
import com.tianji.promotion.strategy.discount.DiscountStrategy;
import com.tianji.promotion.utils.PermuteUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.weaver.ast.Var;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscountServiceImpl implements IDiscountService {

    private final UserCouponMapper userCouponMapper;
    private final ICouponScopeService scopeService;
    private final Executor discountSolutionExecutor;
    private static final int MAX_IN_FLIGHT_TASKS = 5000;

    @Override
    public List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> orderCourses) {
        // 获取当前用户的 id
        Long userId = UserContext.getUser();
        // 查询用户的有效优惠券
        List<Coupon> couponList = userCouponMapper.queryMyCoupons(userId);
        // 判断 couponList 是否为空
        if (CollUtils.isEmpty(couponList)) {
            return CollUtils.emptyList();
        }
        // 初筛,过滤掉门槛金额太高的优惠券
        // 计算订单总价
        int totalAmount = orderCourses.stream().mapToInt(OrderCourseDTO::getPrice).sum();
        List<Coupon> availableCoupons = couponList.stream()
                .filter(c -> DiscountStrategy.getDiscount(c.getDiscountType()).canUse(totalAmount, c))
                .collect(Collectors.toList());
        if (CollUtils.isEmpty(availableCoupons)) {
            return CollUtils.emptyList();
        }
        // 排列组合出所有方案
        // 细筛（找出每一个优惠券的可用的课程，判断课程总价是否达到优惠券的使用需求）
        Map<Coupon, List<OrderCourseDTO>> availableCouponMap = findAvailableCoupon(availableCoupons, orderCourses);
        if (CollUtils.isEmpty(availableCouponMap)) {
            return CollUtils.emptyList();
        }
        // 排列组合
        availableCoupons = new ArrayList<>(availableCouponMap.keySet());
        List<List<Coupon>> solutions = PermuteUtil.permute(availableCoupons);
        // 添加单卷的方案
        for (Coupon coupon : availableCoupons) {
            solutions.add(List.of(coupon));
        }
        // 计算方案的优惠明细

        // 设置许可数量，等于队列容量，代表系统能容纳的最大在途任务数
        Semaphore semaphore = new Semaphore(MAX_IN_FLIGHT_TASKS);

        // 提交任务
        List<CompletableFuture<CouponDiscountDTO>> futures = new ArrayList<>();

        for (List<Coupon> solution : solutions) {
            try {
                // 在提交任务前，获取一个许可。如果许可已满，这里会阻塞主线程
                semaphore.acquire();

                CompletableFuture<CouponDiscountDTO> future = CompletableFuture.supplyAsync(
                        () -> calculateSolutionDiscount(availableCouponMap, orderCourses, solution),
                        discountSolutionExecutor
                ).whenComplete((result, ex) -> {
                    // 任务完成后（无论成功或异常），必须释放许可
                    semaphore.release();
                });
                futures.add(future);

            } catch (InterruptedException e) {
                // 处理中断异常
                Thread.currentThread().interrupt();
                log.error("优惠方案计算任务提交被中断", e);
                break;
            }
        }

        // 等待所有已提交的任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 收集最终结果
        List<CouponDiscountDTO> list = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        return findBestSolution(list);
    }

    @Override
    public CouponDiscountDTO queryDiscountDetailByOrder(OrderCouponDTO orderCouponDTO) {
        // 1.查询用户优惠券
        List<Long> userCouponIds = orderCouponDTO.getUserCouponIds();
        List<Coupon> coupons = userCouponMapper.queryCouponByUserCouponIds(userCouponIds, UserCouponStatus.UNUSED);
        if (CollUtils.isEmpty(coupons)) {
            return null;
        }
        // 2.查询优惠券对应课程
        Map<Coupon, List<OrderCourseDTO>> availableCouponMap = findAvailableCoupon(coupons, orderCouponDTO.getCourseList());
        if (CollUtils.isEmpty(availableCouponMap)) {
            return null;
        }
        // 3.查询优惠券规则
        return calculateSolutionDiscount(availableCouponMap, orderCouponDTO.getCourseList(), coupons);
    }


    private List<CouponDiscountDTO> findBestSolution(List<CouponDiscountDTO> list) {
        // 准备Map记录最优解
        Map<String, CouponDiscountDTO> moreDiscountMap = new HashMap<>();
        Map<Integer, CouponDiscountDTO> lessCouponMap = new HashMap<>();
        for (CouponDiscountDTO solution : list) {
            // 拼接使用到的优惠券
            String ids = solution.getIds().stream()
                    .sorted(Long::compare)
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            // 比较用券相同时，优惠金额是否最大
            CouponDiscountDTO best = moreDiscountMap.get(ids);
            if (best != null && best.getDiscountAmount() >= solution.getDiscountAmount()) {
                continue;
            }
            // 比较优惠金额相同时, 优惠券数量是否最小
            best = lessCouponMap.put(solution.getDiscountAmount(), solution);
            if (best != null && best.getIds().size() <= solution.getIds().size()) {
                continue;
            }
            // 更新最优解
            moreDiscountMap.put(ids, solution);
            lessCouponMap.put(solution.getDiscountAmount(), solution);
        }
        // 求交集
        Collection<CouponDiscountDTO> bestSolution = CollUtil.intersection(lessCouponMap.values(),
                                                                            moreDiscountMap.values());
        // 排序
        return bestSolution.stream()
                .sorted(Comparator.comparing(CouponDiscountDTO::getDiscountAmount).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 计算每一个优惠券方案优惠明细
     * @param availableCouponMap 优惠券及其对应的可以使用的课程集合
     * @param orderCourses 订单中的课程集合
     * @param solution 计算的优惠方案
     * @return 每个方案的可用优惠券及折扣信息
     */
    private CouponDiscountDTO calculateSolutionDiscount(Map<Coupon, List<OrderCourseDTO>> availableCouponMap,
                                                        List<OrderCourseDTO> orderCourses, List<Coupon> solution) {
        // 初始化 CouponDiscountDto 对象
        CouponDiscountDTO dto = new CouponDiscountDTO();
        // 初始化折扣明细的映射
        Map<Long, Integer> detailMap = orderCourses.stream()
                .collect(Collectors.toMap(OrderCourseDTO::getId, oc -> 0));
        dto.setDiscountDetail(detailMap);
        // 计算每张优惠券的优惠情况
        for (Coupon coupon : solution) {
            // 查看哪些课程可以使用这张优惠券
            List<OrderCourseDTO> availableCourses = availableCouponMap.get(coupon);
            // 计算课程总价 (课程原价 - 折扣明细)
            int totalAmount = availableCourses.stream()
                    .mapToInt(oc -> oc.getPrice() - detailMap.get(oc.getId()))
                    .sum();
            // 判断是否达到优惠券门槛
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            if (!discount.canUse(totalAmount, coupon)) {
                // 不满足,直接跳过
                continue;
            }
            // 计算优惠金额
            int discountAmount = discount.calculateDiscount(totalAmount, coupon);
            // 计算优惠明细
            calculateDiscountDetails(detailMap, availableCourses, totalAmount, discountAmount);
            // 更新DTO数据
            dto.getIds().add(coupon.getCreater()); // mapper接口中使用creater承接了优惠券的id
            dto.getRules().add(discount.getRule(coupon));
            dto.setDiscountAmount(discountAmount + dto.getDiscountAmount());
        }
        return dto;
    }

    /**
     * 计算优惠明细
     * @param detailMap 优惠明细的映射,键:课程id,值:折扣价格
     * @param availableCourses 可以参与优惠的订单课程集合
     * @param totalAmount 总金额
     * @param discountAmount 优惠金额
     */
    private void calculateDiscountDetails(Map<Long, Integer> detailMap, List<OrderCourseDTO> availableCourses,
                                          int totalAmount, int discountAmount) {
        // 可以参与优惠的课程数量
        int num = availableCourses.size();
        int remainDiscount = discountAmount;
        // 更新折扣明细
        for (OrderCourseDTO course : availableCourses) {
            int discount = 0;
            if (num-- == 0) {
                // 最后一个课程,直接使用剩余的优惠金额
                discount = remainDiscount;
            } else {
                // 按权重均分优惠金额
                discount = discountAmount *  (course.getPrice() - detailMap.get(course.getId())) / totalAmount;
                remainDiscount -= discount;
            }
            // 更新折扣明细
            detailMap.put(course.getId(), detailMap.get(course.getId()) + discount);
        }
    }

    /**
     * 查询优惠券对应课程
     * @param coupons 优惠卷列表
     * @param courses 课程列表
     * @return
     */
    private Map<Coupon, List<OrderCourseDTO>> findAvailableCoupon(List<Coupon> coupons,
                                                                  List<OrderCourseDTO> courses) {

        Map<Coupon, List<OrderCourseDTO>> map = new HashMap<>(coupons.size());

        // 查询优惠券的限定范围
        List<Long> couponIds = coupons.stream()
                .filter(Coupon::getSpecific)
                .map(Coupon::getId)
                .collect(Collectors.toList());

        // 构建优惠券与课程分类的映射关系
        Map<Long, List<Long>> scopeMap = null;
        if (!couponIds.isEmpty()) {
            // 如果 couponIds 不为空，才执行查询
            List<CouponScope> scopeList = scopeService.lambdaQuery()
                    .in(CouponScope::getCouponId, couponIds)
                    .list();
            // 构建优惠券与课程分类的映射关系
            scopeMap = scopeList.stream()
                    .collect(Collectors.groupingBy(
                            CouponScope::getCouponId, // 按照 couponId 分组
                            Collectors.mapping(CouponScope::getBizId, Collectors.toList()) // 将 bizId 转为列表
                    ));
        }
        for (Coupon coupon : coupons) {
            // 1.找出优惠券的可用的课程
            List<OrderCourseDTO> availableCourses = courses;
            if (coupon.getSpecific() && CollUtils.isNotEmpty(scopeMap)) {
                // 获取优惠券的限定课程类型
                List<Long> bizIds = scopeMap.get(coupon.getId());
                // 筛选课程
                availableCourses = courses.stream()
                        .filter(c -> bizIds.contains(c.getCateId()))
                        .collect(Collectors.toList());
            }
            if (CollUtils.isEmpty(availableCourses)) {
                // 没有任何可用课程，抛弃
                continue;
            }
            // 计算课程总价
            int totalAmount = availableCourses.stream().mapToInt(OrderCourseDTO::getPrice).sum();
            // 判断优惠券是否可用
            if (DiscountStrategy.getDiscount(coupon.getDiscountType()).canUse(totalAmount, coupon)) {
                // 可用
                map.put(coupon, availableCourses);
            }
        }
        return map;
    }
}
