package com.loopers.application.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 미완결 주문 만료 스케줄러.
 *
 * <p>무점유 주문 설계에서 미완결 주문은 두 종류이며, <strong>점유 여부가 달라 처리도 다르다</strong>:
 * <ul>
 *   <li><strong>PENDING</strong> (무점유 견적) — 유저가 결제창에서 이탈한 빈 껍데기.
 *       점유한 자원이 없으므로 상태만 FAILED 로 닫는다. PG 승인은 점유(bind) 후에만
 *       호출되므로 PENDING 주문에 결제가 존재할 수 없다 — PG 조회도 불필요.</li>
 *   <li><strong>PAYMENT_IN_PROGRESS</strong> (점유 완료, 승인 결과 미상) — confirm 도중
 *       크래시/타임아웃으로 남은 건. 무턱대고 복구하면 "결제는 됐는데 주문만 실패" 사고가
 *       되므로, PG 결제 조회로 진실을 확인해 결제됐으면 COMPLETED 확정,
 *       아니면 재고/쿠폰 복구 후 FAILED 처리한다.</li>
 * </ul>
 *
 * <p>이 구분을 못 하면 무점유 PENDING 주문에 재고를 "복구"해서 재고가 불어나는 사고가 난다 —
 * 점유 여부를 상태로 분리한 이유.
 *
 * <p><strong>판정 기준 시각도 다르다</strong>: PENDING 은 주문 생성 시각(orderedAt) 기준 30분,
 * PAYMENT_IN_PROGRESS 는 점유 시작 시각(paymentStartedAt) 기준 10분. 주문을 30분 전에
 * 만들고 방금 confirm 한 건을 승인 도중에 만료시키지 않기 위함이다.
 *
 * <p><strong>동시성</strong>: 보상/확정 모두 주문 행 비관적 락 + 상태 가드로 직렬화되어,
 * 뒤늦게 도착한 confirm 응답 처리와 경합해도 한쪽만 통과한다.
 *
 * <p><strong>알려진 한계</strong>: {@code @Scheduled} 는 다중 인스턴스 환경에서 중복 실행된다.
 * 실서비스에서는 ShedLock 같은 분산 락이 필요하지만 과제 범위(단일 인스턴스)에서는 다루지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PendingOrderExpirationScheduler {

    /** 무점유 견적(PENDING)의 만료 기준 — 주문 생성 후 이 시간 내 confirm 이 없으면 이탈로 간주. */
    private static final int PENDING_EXPIRE_MINUTES = 30;

    /** 점유 완료(PAYMENT_IN_PROGRESS) 건의 판정 기준 — 승인 호출은 수 초면 끝나므로 충분히 보수적인 값. */
    private static final int IN_PROGRESS_RESOLVE_MINUTES = 10;

    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;
    private final OrderTransactionService orderTransactionService;

    @Scheduled(fixedDelay = 60_000)
    public void expireStaleOrders() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        expireAbandonedPendingOrders(now);
        resolveInProgressOrders(now);
    }

    /** 무점유 견적 정리 — 복구할 자원이 없으므로 상태만 닫는다. */
    private void expireAbandonedPendingOrders(ZonedDateTime now) {
        List<OrderModel> abandoned = orderRepository.findByStatusAndOrderedAtBefore(
            OrderStatus.PENDING, now.minusMinutes(PENDING_EXPIRE_MINUTES));
        for (OrderModel order : abandoned) {
            try {
                orderTransactionService.markOrderFailed(order.getId());
                log.info("이탈 PENDING 주문 만료 처리. orderId: {}", order.getId());
            } catch (Exception e) {
                log.warn("PENDING 주문 만료 처리 실패 — orderId: {}", order.getId(), e);
            }
        }
    }

    /** 점유 완료 + 승인 결과 미상 건 — PG 조회로 진실 확인 후 확정/보상. */
    private void resolveInProgressOrders(ZonedDateTime now) {
        List<OrderModel> unresolved = orderRepository.findByStatusAndPaymentStartedAtBefore(
            OrderStatus.PAYMENT_IN_PROGRESS, now.minusMinutes(IN_PROGRESS_RESOLVE_MINUTES));
        for (OrderModel order : unresolved) {
            try {
                resolve(order);
            } catch (Exception e) {
                // 한 건의 실패가 나머지 처리를 막지 않도록 격리
                log.warn("PAYMENT_IN_PROGRESS 주문 판정 실패 — orderId: {}", order.getId(), e);
            }
        }
    }

    private void resolve(OrderModel order) {
        // 점유까지 한 주문 — PG 조회로 "결제는 됐는데 우리만 모르는" 케이스를 먼저 확인
        boolean paidAtPg = paymentGateway.inquire(order.getId())
            .map(PaymentResult::isSuccess)
            .orElse(false);

        if (paidAtPg) {
            orderTransactionService.completePayment(order.getId());
            log.info("PG 조회 결과 결제 완료 확인 — 주문 확정 처리. orderId: {}", order.getId());
        } else {
            orderTransactionService.releaseAndFail(order.getId());
            log.info("미결제 확인 — 점유 자원(재고/쿠폰) 복구. orderId: {}", order.getId());
        }
    }
}
