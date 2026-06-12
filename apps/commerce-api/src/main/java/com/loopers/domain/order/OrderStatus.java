package com.loopers.domain.order;

public enum OrderStatus {
    /** 주문 생성됨 (견적 상태). 재고/쿠폰을 점유하지 않는다 — 이탈해도 자원 잠금 없음. */
    PENDING,
    /** 결제 승인 직전 자원(재고/쿠폰) 점유 완료. 승인 결과 대기 중. */
    PAYMENT_IN_PROGRESS,
    /** 결제 승인 성공으로 주문 확정. */
    COMPLETED,
    /** 주문 실패 — 만료/품절/쿠폰충돌/승인실패. 점유했던 자원은 보상 트랜잭션에서 복구됨. */
    FAILED
}
