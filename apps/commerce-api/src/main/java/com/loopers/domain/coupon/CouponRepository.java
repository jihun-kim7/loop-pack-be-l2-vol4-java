package com.loopers.domain.coupon;

import java.util.List;
import java.util.Optional;

/** 쿠폰 템플릿 저장소 (어드민 관리). */
public interface CouponRepository {
    CouponModel save(CouponModel coupon);
    Optional<CouponModel> findById(Long id);
    List<CouponModel> findAll(int page, int size);
    List<CouponModel> findAllByIds(List<Long> ids);
    void delete(CouponModel coupon);
}
