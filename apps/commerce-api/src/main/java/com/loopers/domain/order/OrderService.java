package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 주문 도메인 협력 Service (스타일 2 - Percival 정통).
 *
 * <p>Repository / 외부 시스템 의존 없이 호출자가 조회해서 넘긴 도메인 객체만으로 협력한다.
 * 모든 영속성 호출과 트랜잭션 경계는 {@link com.loopers.application.order.OrderTransactionService}가 책임진다.
 *
 * <p>한 줄 단위 문맥은 {@link OrderLine} 으로 묶어서 받는다. 여러 리스트(products/stocks/items)를
 * 인덱스로 매칭하던 약한 결합을 제거한다.
 */
@Service
public class OrderService {

    /**
     * 주문 생성 도메인 협력 — 재고 확인 + 주문 항목 생성 + 원금 확정.
     *
     * <p>재고는 <strong>확인만</strong> 하고 차감하지 않는다 (무점유 견적).
     * 실제 차감은 결제 승인 직전({@code OrderTransactionService.bindResources})에
     * 조건부 원자 UPDATE 로 수행된다 — 이 확인은 품절 상품으로 결제창까지 가는 것을
     * 막는 UX 게이트이며, 동시성 보장은 차감 시점의 원자 UPDATE 가 책임진다.
     */
    public OrderModel createOrder(Long userId, List<OrderLine> orderLines) {
        OrderModel order = new OrderModel(userId);
        for (OrderLine line : orderLines) {
            if (!line.stock().hasEnough(line.quantity())) {
                throw new CoreException(ErrorType.BAD_REQUEST,
                    "[productId = " + line.product().getId() + "] 재고가 부족합니다.");
            }
            order.addItem(new OrderItemModel(
                order,
                line.product().getId(),
                line.product().getName(),
                line.product().getPrice(),
                line.quantity()
            ));
        }
        order.confirmAmounts();
        return order;
    }
}
