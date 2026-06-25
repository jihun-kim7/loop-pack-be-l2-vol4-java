package com.loopers.application.payment;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.PgGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * pg-simulator 비동기 결제 대사 스케줄러.
 *
 * <p>PAYMENT_IN_PROGRESS 상태로 남은 주문을 대상으로 pg-simulator 에 orderId 로 트랜잭션 목록을 조회해
 * 최종 결제 상태를 확인하고 주문을 확정한다.
 *
 * <h2>판정 기준</h2>
 * <ul>
 *   <li>SUCCESS 있음 → 주문 완료 처리</li>
 *   <li>PENDING 있음 → 콜백 미도착, 이번 실행 건너뜀 (다음 스케줄 재확인)</li>
 *   <li>SUCCESS·PENDING 없음 (모두 FAILED 또는 기록 없음) → 실패 처리</li>
 * </ul>
 *
 * <p>재시도·콜백과 경합 시 {@link PaymentApplicationService#handleCallback} 의
 * {@code isTerminal()} 가드가 중복 처리를 막는다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PgReconcileScheduler {

    private static final int RESOLVE_MINUTES = 10;

    private final OrderRepository orderRepository;
    private final PgGateway pgGateway;
    private final PaymentApplicationService paymentApplicationService;

    @Scheduled(fixedDelay = 60_000)
    public void reconcile() {
        ZonedDateTime cutoff = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(RESOLVE_MINUTES);
        List<OrderModel> staleOrders = orderRepository.findByStatusAndPaymentStartedAtBefore(
            OrderStatus.PAYMENT_IN_PROGRESS, cutoff);

        for (OrderModel order : staleOrders) {
            try {
                reconcileOne(order);
            } catch (Exception e) {
                log.warn("[PgReconcile] 처리 실패 — orderId={}", order.getId(), e);
            }
        }
    }

    private void reconcileOne(OrderModel order) {
        Long orderId = order.getId();
        String userId = order.getUserId().toString();

        List<PgGateway.PgTransactionResult> txList = pgGateway.findTransactionsByOrderId(userId, orderId.toString());
        if (txList.isEmpty()) {
            log.debug("[PgReconcile] pg-simulator 기록 없음 — 건너뜀. orderId={}", orderId);
            return;
        }

        // SUCCESS 우선
        txList.stream()
            .filter(t -> "SUCCESS".equals(t.status()))
            .findFirst()
            .ifPresent(t -> {
                paymentApplicationService.handleCallback(t.transactionKey(), orderId, "SUCCESS", null);
                log.info("[PgReconcile] 결제 성공 확인 → 주문 완료. orderId={}", orderId);
            });

        if (txList.stream().anyMatch(t -> "SUCCESS".equals(t.status()))) return;

        // PENDING 있으면 콜백 대기
        if (txList.stream().anyMatch(t -> "PENDING".equals(t.status()))) {
            log.debug("[PgReconcile] PENDING 존재 — 콜백 대기. orderId={}", orderId);
            return;
        }

        // SUCCESS도 PENDING도 없음 → 실패 처리
        txList.stream()
            .filter(t -> "FAILED".equals(t.status()))
            .findFirst()
            .ifPresentOrElse(
                t -> {
                    paymentApplicationService.handleCallback(t.transactionKey(), orderId, "FAILED", t.reason());
                    log.info("[PgReconcile] 결제 실패 확인 → 자원 복구. orderId={}, reason={}", orderId, t.reason());
                },
                () -> log.warn("[PgReconcile] 트랜잭션 없음 — 건너뜀. orderId={}", orderId)
            );
    }
}
