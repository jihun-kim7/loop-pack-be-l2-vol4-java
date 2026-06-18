package com.loopers.domain.coupon;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

/**
 * 쿠폰 할인 타입.
 *
 * <ul>
 *   <li>{@link #FIXED} 정액 — value 는 할인 금액(원). 주문금액을 초과하면 주문금액으로 캡.</li>
 *   <li>{@link #RATE} 정률 — value 는 할인율(%). 0~100. 내림(floor) 계산.</li>
 * </ul>
 *
 * <p>할인 계산 로직을 타입별로 캡슐화하여 if/switch 분기를 도메인에 흩뿌리지 않는다.
 */
public enum CouponType {
    FIXED {
        @Override
        public Money calculateDiscount(Money orderAmount, long value) {
            Money fixed = Money.of(value);
            // 할인액이 주문금액을 초과하면 주문금액까지만 할인 (최종 결제금액 음수 방지)
            return orderAmount.isGreaterThanOrEqual(fixed) ? fixed : orderAmount;
        }

        @Override
        public void validateValue(long value) {
            if (value < 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "정액 쿠폰의 할인 금액은 0 이상이어야 합니다.");
            }
        }
    },
    RATE {
        @Override
        public Money calculateDiscount(Money orderAmount, long value) {
            return orderAmount.applyRate((int) value);
        }

        @Override
        public void validateValue(long value) {
            if (value < 0 || value > 100) {
                throw new CoreException(ErrorType.BAD_REQUEST, "정률 쿠폰의 할인율은 0 이상 100 이하여야 합니다.");
            }
        }
    };

    /** 주문 금액에 대한 할인 금액을 계산한다. */
    public abstract Money calculateDiscount(Money orderAmount, long value);

    /** 타입별 value 유효성 검증. */
    public abstract void validateValue(long value);
}
