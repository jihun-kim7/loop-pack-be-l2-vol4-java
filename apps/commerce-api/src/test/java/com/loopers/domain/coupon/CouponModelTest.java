package com.loopers.domain.coupon;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponModelTest {

    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(30);

    @DisplayName("쿠폰 템플릿을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("정상 값으로 생성된다.")
        @Test
        void createsCoupon_whenValid() {
            CouponModel coupon = new CouponModel("10% 할인", CouponType.RATE, 10, 10_000L, FUTURE);
            assertThat(coupon.getType()).isEqualTo(CouponType.RATE);
            assertThat(coupon.getValue()).isEqualTo(10L);
        }

        @DisplayName("이름이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameBlank() {
            CoreException result = assertThrows(CoreException.class,
                () -> new CouponModel("  ", CouponType.FIXED, 1000, null, FUTURE));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정률 쿠폰의 할인율이 100을 초과하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenRateOver100() {
            CoreException result = assertThrows(CoreException.class,
                () -> new CouponModel("초과", CouponType.RATE, 101, null, FUTURE));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("만료 시각이 없으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenExpiredAtNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new CouponModel("쿠폰", CouponType.FIXED, 1000, null, null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("할인 금액을 계산할 때,")
    @Nested
    class CalculateDiscount {

        @DisplayName("정액 쿠폰은 고정 금액을 할인한다.")
        @Test
        void fixedDiscount() {
            CouponModel coupon = new CouponModel("3천원 할인", CouponType.FIXED, 3000, null, FUTURE);
            Money discount = coupon.calculateDiscount(Money.of(10_000L));
            assertThat(discount.getAmount()).isEqualTo(3000L);
        }

        @DisplayName("정액 쿠폰 할인액이 주문금액을 초과하면 주문금액까지만 할인한다 (음수 결제 방지).")
        @Test
        void fixedDiscount_cappedAtOrderAmount() {
            CouponModel coupon = new CouponModel("만원 할인", CouponType.FIXED, 10_000, null, FUTURE);
            Money discount = coupon.calculateDiscount(Money.of(3_000L));
            assertThat(discount.getAmount()).isEqualTo(3_000L);
        }

        @DisplayName("정률 쿠폰은 비율만큼 내림으로 할인한다.")
        @Test
        void rateDiscount() {
            CouponModel coupon = new CouponModel("10% 할인", CouponType.RATE, 10, null, FUTURE);
            Money discount = coupon.calculateDiscount(Money.of(33_333L));
            assertThat(discount.getAmount()).isEqualTo(3_333L);   // 3,333.3 내림
        }

    }

    @DisplayName("적용 가능 여부를 검증할 때,")
    @Nested
    class ValidateApplicable {

        @DisplayName("만료된 쿠폰이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenExpired() {
            ZonedDateTime now = ZonedDateTime.now();
            CouponModel coupon = new CouponModel("만료", CouponType.FIXED, 1000, null, now.minusSeconds(1));
            CoreException result = assertThrows(CoreException.class,
                () -> coupon.validateApplicable(Money.of(10_000L), now));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("최소 주문 금액 미달이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBelowMinOrderAmount() {
            CouponModel coupon = new CouponModel("조건부", CouponType.FIXED, 1000, 10_000L, FUTURE);
            CoreException result = assertThrows(CoreException.class,
                () -> coupon.validateApplicable(Money.of(9_999L), ZonedDateTime.now()));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("유효하고 최소 금액 조건을 충족하면 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenApplicable() {
            CouponModel coupon = new CouponModel("조건부", CouponType.FIXED, 1000, 10_000L, FUTURE);
            coupon.validateApplicable(Money.of(10_000L), ZonedDateTime.now());
        }
    }

    @DisplayName("만료 여부는,")
    @Nested
    class Expiry {

        @DisplayName("만료 시각이 현재보다 과거면 만료로 판단한다.")
        @Test
        void isExpired() {
            ZonedDateTime now = ZonedDateTime.now();
            CouponModel expired = new CouponModel("만료", CouponType.FIXED, 1000, null, now.minusSeconds(1));
            CouponModel valid = new CouponModel("유효", CouponType.FIXED, 1000, null, now.plusDays(1));
            assertThat(expired.isExpired(now)).isTrue();
            assertThat(valid.isExpired(now)).isFalse();
        }
    }
}
