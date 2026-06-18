package com.loopers.support.error;

/**
 * 결제 실패 전용 예외.
 *
 * <p>결제 승인(confirm)이 PG 에서 거절됐을 때, 점유했던 자원(재고/쿠폰)을
 * 보상 트랜잭션으로 복구하고 주문을 FAILED 처리한 뒤 throw 한다.
 * 사용자에게는 400 BAD_REQUEST 로 결제 실패가 응답된다.
 */
public class PaymentFailedException extends CoreException {

    public PaymentFailedException(String message) {
        super(ErrorType.BAD_REQUEST, message);
    }
}
