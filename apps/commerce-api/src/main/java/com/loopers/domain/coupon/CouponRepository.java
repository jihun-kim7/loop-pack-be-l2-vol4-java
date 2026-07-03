package com.loopers.domain.coupon;

import java.util.List;
import java.util.Optional;

/** 쿠폰 템플릿 저장소 (어드민 관리). */
public interface CouponRepository {
    CouponModel save(CouponModel coupon);
    Optional<CouponModel> findById(Long id);
    List<CouponModel> findAll(int page, int size);
    void delete(CouponModel coupon);

    /**
     * 선착순 수량 차감 — 조건부 원자 증가.
     *
     * <p>{@code issued_count < total_quantity} 일 때만 +1 (무제한(null)이면 항상 +1).
     * "확인 후 증가"가 아니라 "증가하면서 확인"이므로 동시 발급에도 초과가 불가능하다.
     *
     * @return 1 = 차감 성공, 0 = 수량 소진(매진)
     */
    int tryIncreaseIssuedCount(Long couponId);
}
