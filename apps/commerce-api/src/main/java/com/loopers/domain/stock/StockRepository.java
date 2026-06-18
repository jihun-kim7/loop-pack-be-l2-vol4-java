package com.loopers.domain.stock;

import java.util.List;
import java.util.Optional;

public interface StockRepository {
    StockModel save(StockModel stock);
    Optional<StockModel> findByProductId(Long productId);
    List<StockModel> findAllByProductIdIn(List<Long> productIds);

    /**
     * 조건부 원자 차감 — {@code UPDATE ... SET quantity = quantity - ? WHERE quantity >= ?}.
     *
     * <p>읽기-계산-쓰기 틈이 없어 동시 차감에도 재고 음수가 발생하지 않는다.
     * 결제 승인 직전의 자원 점유 단계에서 사용된다.
     *
     * @return 영향받은 행 수. 0이면 재고 부족 (상품 존재는 주문 시점에 이미 검증됨)
     */
    int deductAtomically(Long productId, int quantity);

    /**
     * 원자 복구 — 차감의 역연산. 승인 실패/미결제 확인 시 보상에 사용된다.
     *
     * @return 영향받은 행 수
     */
    int restoreAtomically(Long productId, int quantity);
}
