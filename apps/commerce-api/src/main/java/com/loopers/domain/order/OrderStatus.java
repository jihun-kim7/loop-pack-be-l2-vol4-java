package com.loopers.domain.order;

public enum OrderStatus {
    /** 주문 생성 + 재고/쿠폰 점유 완료, 결제 결과 대기. */
    PENDING,
    /** 결제 성공으로 주문 확정. */
    COMPLETED,
    /** 결제 실패 — 재고/쿠폰은 보상 트랜잭션에서 복구됨. */
    FAILED
}
