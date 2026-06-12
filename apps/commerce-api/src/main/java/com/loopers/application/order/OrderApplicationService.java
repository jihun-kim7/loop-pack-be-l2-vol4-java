package com.loopers.application.order;

import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 주문 유스케이스 Application Service (스타일 2 - Percival 정통).
 *
 * <p><strong>무점유 주문 + 승인 직전 점유 (Round 4)</strong> — 실제 PG 의 인증 → 승인
 * 2단계 모델을 따라 주문 생성과 결제 확정이 서로 다른 HTTP 요청으로 분리되어 있고,
 * 자원(재고/쿠폰) 점유는 결제 승인 직전으로 미뤄진다:
 *
 * <ol>
 *   <li><strong>POST /orders (여기)</strong> — 재고 확인 + 쿠폰 검증/할인 계산 + 주문 PENDING 저장.
 *       <strong>아무 자원도 점유하지 않는 견적</strong>이다. 프론트는 응답받은 orderId/금액으로
 *       PG 결제창(인증)을 연다.</li>
 *   <li><strong>(프론트-PG 구간)</strong> — 유저가 결제창에서 인증. 서버는 관여하지 않으며,
 *       유저가 이탈해도 잠긴 자원이 없다.</li>
 *   <li><strong>POST /payments/confirm</strong> ({@link com.loopers.application.payment.PaymentApplicationService})
 *       — 금액 위변조 검증 → <strong>자원 점유(재고 원자 차감 + 쿠폰 확정)</strong> → PG 승인 →
 *       성공 시 COMPLETED / 실패 시 보상. 점유 실패자는 승인 전에 탈락하므로 청구되지 않는다.</li>
 * </ol>
 *
 * <p><strong>미완결 주문 정리</strong>: {@link PendingOrderExpirationScheduler}가 이탈한
 * PENDING(무점유 — 상태만 닫음)과 승인 결과 미상인 PAYMENT_IN_PROGRESS(PG 조회 후 확정/보상)를
 * 주기적으로 처리한다.
 */
@RequiredArgsConstructor
@Service
public class OrderApplicationService {

    private final OrderTransactionService orderTransactionService;
    private final OrderRepository orderRepository;

    /**
     * 주문 생성 — 무점유 견적. 재고 확인/쿠폰 검증과 금액 확정만 수행하고 PENDING 으로 저장한다.
     *
     * <p>응답의 orderId/totalPrice 가 프론트의 PG 결제창 호출 파라미터가 된다.
     * 자원 점유(재고 차감/쿠폰 사용)는 결제 승인 직전(confirm)에 일어난다.
     *
     * @param couponId 적용할 발급 쿠폰(UserCoupon) id. 미적용 시 null.
     */
    public OrderInfo createOrder(Long userId, List<OrderItemCommand> items, Long couponId) {
        OrderModel pending = orderTransactionService.createPendingOrder(userId, items, couponId);
        return OrderInfo.from(pending);
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> getOrders(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        return orderRepository.findByUserIdAndOrderedAtBetween(userId, startAt, endAt).stream()
            .map(OrderInfo::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrder(Long userId, Long orderId) {
        OrderModel order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
        if (!order.getUserId().equals(userId)) {
            // 본인 외 주문 접근은 존재 자체를 노출하지 않기 위해 404로 응답 (P-11)
            throw new CoreException(ErrorType.NOT_FOUND,
                "[id = " + orderId + "] 주문을 찾을 수 없습니다.");
        }
        return OrderInfo.from(order);
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> getAllOrders(int page, int size) {
        return orderRepository.findAll(page, size).stream()
            .map(OrderInfo::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrderAdmin(Long orderId) {
        OrderModel order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
        return OrderInfo.from(order);
    }
}
