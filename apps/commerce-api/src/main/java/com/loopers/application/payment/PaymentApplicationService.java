package com.loopers.application.payment;

import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderTransactionService;
import com.loopers.domain.payment.PaymentResult;
import com.loopers.domain.payment.PaymentService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.error.PaymentFailedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 결제 확정 유스케이스 Application Service — 오케스트레이터.
 *
 * <p>프론트가 PG 결제창에서 인증을 마치고 successUrl 로 돌아오면, paymentKey 를 들고
 * 이 confirm 을 호출한다. <strong>핵심 순서: 자원 점유(재고 차감 + 쿠폰 확정) → PG 승인.</strong>
 * 점유에 실패한 요청은 승인을 호출하지 않으므로 유저에게 청구되지 않는다 —
 * "결제됐는데 품절" 환불이 정상 흐름에서 발생하지 않는 이유.
 *
 * <ol>
 *   <li><strong>검증 (TX, readonly)</strong> — 소유자 / PENDING 상태 / 금액 위변조 확인.</li>
 *   <li><strong>자원 점유 (TX2a)</strong> — 재고 조건부 원자 차감 + 쿠폰 낙관적 락 확정.
 *       실패 시 견적 폐기(FAILED) 후 즉시 응답 — 돈이 나가지 않은 상태.</li>
 *   <li><strong>PG 승인 호출 (트랜잭션 밖)</strong> — DB 커넥션 비점유.
 *       결제 시도 기록은 {@link PaymentService} 자체 트랜잭션으로 독립 커밋.</li>
 *   <li><strong>결과 반영 (TX2b)</strong> — 성공: COMPLETED / 실패: 점유 자원 복구 + FAILED.</li>
 * </ol>
 *
 * <p><strong>TIMEOUT 은 보상하지 않는다</strong>: 타임아웃은 "실패"가 아니라 "결과 미확인"이다.
 * PG 에서는 승인이 완료됐을 수 있으므로 여기서 점유를 풀면 "돈은 나갔는데 주문은 실패"
 * 사고가 된다. PAYMENT_IN_PROGRESS 로 남겨두고, 만료 스케줄러가 PG 결제 조회로 진실을
 * 확인한 뒤 확정/보상을 결정한다.
 */
@RequiredArgsConstructor
@Service
public class PaymentApplicationService {

    private final OrderTransactionService orderTransactionService;
    private final PaymentService paymentService;

    public OrderInfo confirmPayment(Long userId, String paymentKey, Long orderId, Long amount) {
        // 1. 소유자 / 상태 / 금액 위변조 검증
        orderTransactionService.validateConfirmable(userId, orderId, amount);

        // 2. 자원 점유 — 재고 원자 차감 + 쿠폰 확정. 실패 시 승인을 호출하지 않는다 (청구 없음)
        try {
            orderTransactionService.bindResources(orderId);
        } catch (CoreException e) {
            // 점유 실패(품절/쿠폰 충돌/만료) — 견적 폐기. 다른 confirm 이 선점한 경우는 건드리지 않음
            orderTransactionService.markOrderFailed(orderId);
            throw e;
        }

        // 3. PG 승인 호출 — 트랜잭션 밖 (결제 시도 기록은 PaymentService 자체 트랜잭션으로 보존)
        PaymentResult result = paymentService.confirm(paymentKey, orderId, amount);

        // 4-a. 성공 — 주문 확정
        if (result.isSuccess()) {
            return orderTransactionService.completePayment(orderId);
        }

        // 4-b. 타임아웃 — 결과 미확인. 점유 유지 (스케줄러가 PG 조회 후 확정/보상 판정)
        if (result.status() == PaymentResult.Status.TIMEOUT) {
            throw new CoreException(ErrorType.INTERNAL_ERROR,
                "결제 결과를 확인하지 못했습니다. 잠시 후 주문 내역에서 결제 상태를 확인해주세요.");
        }

        // 4-c. 승인 실패 — 점유 자원 보상: 재고 복구 + 쿠폰 복구 + 주문 FAILED
        orderTransactionService.releaseAndFail(orderId);
        throw new PaymentFailedException("PAYMENT_FAILED: " + result.failureReasonOrDefault());
    }
}
