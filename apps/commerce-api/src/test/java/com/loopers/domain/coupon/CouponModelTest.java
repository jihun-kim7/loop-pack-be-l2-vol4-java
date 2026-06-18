package com.loopers.domain.coupon;

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

    // 할인 계산/적용 가능 검증은 발급 시점 혜택 스냅샷을 가진 UserCouponModel 의 책임으로 이동했다.
    // 해당 테스트는 UserCouponModelTest 가 담당한다.

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
