package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserCouponModelTest {

    private static final ZonedDateTime NOW = ZonedDateTime.now();
    private static final ZonedDateTime FUTURE = NOW.plusDays(30);
    private static final ZonedDateTime PAST = NOW.minusDays(1);

    @DisplayName("발급할 때,")
    @Nested
    class Issue {

        @DisplayName("AVAILABLE 상태로 생성된다.")
        @Test
        void createsAvailable_whenIssued() {
            UserCouponModel uc = new UserCouponModel(1L, 100L, FUTURE);
            assertThat(uc.getStatus()).isEqualTo(CouponStatus.AVAILABLE);
            assertThat(uc.getUserId()).isEqualTo(1L);
        }
    }

    @DisplayName("사용할 때,")
    @Nested
    class Use {

        @DisplayName("AVAILABLE 쿠폰은 USED 로 전이된다.")
        @Test
        void transitionsToUsed_whenAvailable() {
            UserCouponModel uc = new UserCouponModel(1L, 100L, FUTURE);
            uc.use(NOW);
            assertThat(uc.getStatus()).isEqualTo(CouponStatus.USED);
            assertThat(uc.getUsedAt()).isNotNull();
        }

        @DisplayName("이미 사용된 쿠폰을 다시 사용하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAlreadyUsed() {
            UserCouponModel uc = new UserCouponModel(1L, 100L, FUTURE);
            uc.use(NOW);
            CoreException result = assertThrows(CoreException.class, () -> uc.use(NOW));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

    }

    @DisplayName("사용을 취소할 때 (결제 실패 보상),")
    @Nested
    class CancelUse {

        @DisplayName("USED 쿠폰은 AVAILABLE 로 되돌아가고 usedAt 이 초기화된다.")
        @Test
        void revertsToAvailable_whenUsed() {
            UserCouponModel uc = new UserCouponModel(1L, 100L, FUTURE);
            uc.use(NOW);

            uc.cancelUse();

            assertThat(uc.getStatus()).isEqualTo(CouponStatus.AVAILABLE);
            assertThat(uc.getUsedAt()).isNull();
        }

        @DisplayName("사용되지 않은 쿠폰을 취소하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNotUsed() {
            UserCouponModel uc = new UserCouponModel(1L, 100L, FUTURE);

            CoreException result = assertThrows(CoreException.class, uc::cancelUse);
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("소유자 확인은,")
    @Nested
    class Ownership {

        @DisplayName("발급받은 유저면 true, 아니면 false 를 반환한다.")
        @Test
        void returnsOwnership() {
            UserCouponModel uc = new UserCouponModel(1L, 100L, FUTURE);
            assertThat(uc.isOwnedBy(1L)).isTrue();
            assertThat(uc.isOwnedBy(2L)).isFalse();
        }
    }

    @DisplayName("표시 상태는,")
    @Nested
    class DisplayStatus {

        @DisplayName("미사용 + 미만료면 AVAILABLE.")
        @Test
        void returnsAvailable_whenUnusedAndNotExpired() {
            UserCouponModel uc = new UserCouponModel(1L, 100L, FUTURE);
            assertThat(uc.displayStatus(NOW)).isEqualTo(CouponStatus.AVAILABLE);
        }

        @DisplayName("미사용이지만 만료되었으면 EXPIRED.")
        @Test
        void returnsExpired_whenUnusedButExpired() {
            UserCouponModel uc = new UserCouponModel(1L, 100L, PAST);
            assertThat(uc.displayStatus(NOW)).isEqualTo(CouponStatus.EXPIRED);
        }

        @DisplayName("사용 완료면 만료 여부와 무관하게 USED.")
        @Test
        void returnsUsed_whenUsed() {
            UserCouponModel uc = new UserCouponModel(1L, 100L, FUTURE);
            uc.use(NOW);
            assertThat(uc.displayStatus(NOW)).isEqualTo(CouponStatus.USED);
        }
    }
}
