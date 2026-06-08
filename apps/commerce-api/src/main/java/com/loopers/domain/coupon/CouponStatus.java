package com.loopers.domain.coupon;

/**
 * 발급된 사용자 쿠폰의 상태.
 *
 * <ul>
 *   <li>{@link #AVAILABLE} 사용 가능 (미사용 + 미만료)</li>
 *   <li>{@link #USED} 사용 완료 (재사용 불가)</li>
 *   <li>{@link #EXPIRED} 만료 — 저장 상태가 아니라 만료 시각 기준으로 조회 시점에 파생되는 표시 상태</li>
 * </ul>
 */
public enum CouponStatus {
    AVAILABLE,
    USED,
    EXPIRED
}
