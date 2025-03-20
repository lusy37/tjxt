package com.tianji.promotion.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CategoryClient;
import com.tianji.api.dto.course.CategoryBasicDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.pojo.Coupon;
import com.tianji.promotion.domain.pojo.CouponScope;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponScopeVO;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ObtainType;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.service.IExchangeCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 优惠券的规则信息 服务实现类
 * </p>
 *
 * @author lusy
 * @since 2025-03-19
 */
@Service
@RequiredArgsConstructor
public class  CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {

    private final CouponScopeServiceImpl scopeService;
    private final IExchangeCodeService codeService;
    private final CategoryClient categoryClient;
    @Override
    @Transactional
    public void saveCoupon(CouponFormDTO couponFormDTO) {
        // 保存优惠券
        Coupon coupon = BeanUtil.copyProperties(couponFormDTO, Coupon.class);
        save(coupon);

        // 判断是否限定了使用范围
        if (!couponFormDTO.getSpecific()) {
            return;
        }

        Long couponId = coupon.getId();
        // 2.保存限定范围
        List<Long> scopes = couponFormDTO.getScopes();
        if (CollUtils.isEmpty(scopes)) {
            throw new BadRequestException("限定范围不能为空");
        }
        // 2.1.转换PO
        List<CouponScope> list = scopes.stream()
                .map(bizId -> new CouponScope().setBizId(bizId).setCouponId(couponId).setType(1))
                .collect(Collectors.toList());
        // 2.2.保存
        scopeService.saveBatch(list);
    }

    @Override
    public PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query) {

        // 组装分页查询条件
        Page<Coupon> page = lambdaQuery()
                .eq(ObjectUtil.isNotNull(query.getType()), Coupon::getDiscountType, query.getType())
                .eq(ObjectUtil.isNotNull(query.getStatus()), Coupon::getStatus, query.getStatus())
                .like(StrUtil.isNotBlank(query.getName()), Coupon::getName, query.getName())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<Coupon> records = page.getRecords();

        if (ObjectUtil.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        // 组装VO
        List<CouponPageVO> list = BeanUtils.copyList(records, CouponPageVO.class);
        // 3.返回
        return PageDTO.of(page, list);
    }

    @Override
    @Transactional
    public void beginIssue(Long id, CouponIssueFormDTO dto) {

        // 校验 id 是否匹配
        if (id == null || !id.equals(dto.getId())) {
            throw new BizIllegalException("参数校验异常");
        }

        // 根据 id 获取优惠券
        Coupon coupon = getById(id);
        if (coupon == null) {
            throw new BizIllegalException("优惠券不存在");
        }

        if (coupon.getStatus() != CouponStatus.DRAFT && coupon.getStatus() != CouponStatus.PAUSE) {
            throw new BizIllegalException("优惠券状态异常");
        }

        LocalDateTime issueBeginTime = dto.getIssueBeginTime();
        LocalDateTime now = LocalDateTime.now();

        boolean isBegin = issueBeginTime == null || !issueBeginTime.isAfter(now);

        Coupon c = BeanUtil.copyProperties(dto, Coupon.class);

        if (isBegin) {
            c.setIssueBeginTime(now);
            c.setStatus(CouponStatus.ISSUING);
        }else {
            c.setStatus(CouponStatus.UN_ISSUE);
        }
        // 更新优惠券
        updateById(c);

        // 判断是否需要生成兑换码，优惠券类型必须是兑换码，优惠券状态必须是待发放
        if (coupon.getObtainWay() == ObtainType.ISSUE && coupon.getStatus() == CouponStatus.DRAFT) {
            coupon.setIssueEndTime(dto.getIssueEndTime());
            codeService.asyncGenerateExchangeCode(coupon);
        }
    }

    @Override
    @Transactional
    public void updateCoupon(Long id, CouponFormDTO couponFormDTO) {

        if (!Objects.equals(id, couponFormDTO.getId())) {
            throw  new BizIllegalException("参数校验异常");
        }

        // 校验优惠券的状态是否为待发放
        Coupon coupon = getById(couponFormDTO.getId());
        if (!(coupon.getStatus() == CouponStatus.DRAFT)) {
            throw new BizIllegalException("优惠券的状态不处于待发放");
        }
        // 判断之前是否限定了使用范围
        if (coupon.getSpecific()) {
           scopeService.lambdaUpdate()
                   .eq(CouponScope::getCouponId, coupon.getId())
                   .remove();
        }
        // 更新优惠券
        Coupon c = BeanUtil.copyProperties(couponFormDTO, Coupon.class);
        updateById(c);
        if (!couponFormDTO.getSpecific()) {
            return;
        }
        // 保存新的限定使用范围表
        List<Long> scopes = couponFormDTO.getScopes();
        if (CollUtils.isEmpty(scopes)) {
            throw new BadRequestException("限定范围不能为空");
        }
        List<CouponScope> list = scopes.stream()
                .map(bizId -> new CouponScope().setBizId(bizId).setCouponId(coupon.getId()).setType(1))
                .collect(Collectors.toList());
        scopeService.saveBatch(list);
    }

    @Override
    public void deleteCouponById(Long id) {
        // 1.校验优惠券是否存在
        Coupon coupon = getById(id);
        if (coupon == null) {
            throw new BizIllegalException("优惠券不存在");
        }
        // 2.校验优惠券的状态是否为待发放
        if (!(coupon.getStatus() == CouponStatus.DRAFT)) {
            throw new BizIllegalException("优惠券的状态不处于待发放");
        }
        // 3.删除优惠券
        removeById(id);
        // 4.删除优惠券的限定范围
        if (!coupon.getSpecific()) {
            return;
        }
        scopeService.lambdaUpdate()
                .eq(CouponScope::getCouponId, id)
                .remove();
    }

    @Override
    public CouponDetailVO getCouponById(Long id) {
        // 根据 id 获取相关优惠券
        Coupon coupon = getById(id);
        if (coupon == null) {
            throw new BizIllegalException("优惠券不存在");
        }
        // 判断是否限定使用范围
        Set<Long> bizIds = null;
        Map<Long, String> categoryMap = null;
        if (coupon.getSpecific()) {
            // 查询限定的使用范围
            List<CouponScope> list = scopeService.lambdaQuery()
                    .eq(CouponScope::getCouponId, id)
                    .list();
            if (CollUtils.isEmpty(list)) {
                throw new BizIllegalException("限定范围不存在");
            }
            bizIds = list.stream().map(CouponScope::getBizId).collect(Collectors.toSet());
            // 根据业务id查询具体名称
            List<CategoryBasicDTO> categoryBasicDTOS = categoryClient.getAllOfOneLevel();
            if (!CollUtils.isEmpty(categoryBasicDTOS)){
                categoryMap = categoryBasicDTOS.stream()
                        .collect(Collectors.toMap(CategoryBasicDTO::getId, CategoryBasicDTO::getName));
            }
        }
        // 封装VO
        CouponDetailVO vo = BeanUtil.copyProperties(coupon, CouponDetailVO.class);
        if (bizIds != null && categoryMap != null) {
            List <CouponScopeVO> scopes = new ArrayList<>();
            for (Long bizId : bizIds) {
                String name = categoryMap.get(bizId);
                scopes.add(new CouponScopeVO(bizId, name));
            }
            vo.setScopes(scopes);
        }
        return vo;
    }

    @Override
    public void pauseIssue(Long id) {
        // 根据id查询优惠券
        Coupon coupon = getById(id);
        if (coupon == null) {
            throw new BizIllegalException("优惠券不存在");
        }
        if (coupon.getStatus() != CouponStatus.ISSUING) {
            throw new BizIllegalException("优惠券状态异常");
        }
        coupon.setStatus(CouponStatus.PAUSE);
        updateById(coupon);
    }

}
