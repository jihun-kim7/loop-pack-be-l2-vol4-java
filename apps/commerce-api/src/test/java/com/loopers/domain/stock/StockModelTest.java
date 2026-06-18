package com.loopers.domain.stock;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockModelTest {

    @DisplayName("재고를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("0 이상의 수량으로 생성하면 정상적으로 생성된다.")
        @ParameterizedTest
        @ValueSource(ints = {0, 1, 10, 1000})
        void createsStock_whenQuantityIsNonNegative(int quantity) {
            // act
            StockModel stock = StockModel.of(1L, quantity);

            // assert
            assertAll(
                () -> assertThat(stock.getProductId()).isEqualTo(1L),
                () -> assertThat(stock.getQuantity()).isEqualTo(quantity)
            );
        }

        @DisplayName("음수 수량으로 생성하면 BAD_REQUEST 예외가 발생한다 (Quantity VO 검증).")
        @Test
        void throwsBadRequest_whenQuantityIsNegative() {
            CoreException result = assertThrows(CoreException.class, () -> StockModel.of(1L, -1));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    // 재고 차감/복구는 엔티티 메서드가 아니라 조건부 원자 UPDATE(StockRepository)로 수행된다.
    // 동시성 보장을 포함한 차감/복구 검증은 OrderTransactionServiceIntegrationTest 가 담당한다.

    @DisplayName("hasEnough 는,")
    @Nested
    class HasEnough {

        @DisplayName("보유 수량 이하 요청이면 true, 초과면 false 를 반환한다.")
        @Test
        void checksAvailability() {
            StockModel stock = StockModel.of(1L, 5);
            assertAll(
                () -> assertThat(stock.hasEnough(5)).isTrue(),
                () -> assertThat(stock.hasEnough(6)).isFalse()
            );
        }
    }

    @DisplayName("재고 표시 정책은,")
    @Nested
    class DisplayPolicy {

        @DisplayName("isAvailable: 1개 이상이면 true.")
        @Test
        void isAvailable() {
            assertAll(
                () -> assertThat(StockModel.of(1L, 1).isAvailable()).isTrue(),
                () -> assertThat(StockModel.of(1L, 0).isAvailable()).isFalse()
            );
        }

        @DisplayName("getDisplayQuantity: 10개 이하면 수량 노출, 초과면 null.")
        @Test
        void displayQuantityIs10OrLess() {
            assertAll(
                () -> assertThat(StockModel.of(1L, 10).getDisplayQuantity()).isEqualTo(10),
                () -> assertThat(StockModel.of(1L, 11).getDisplayQuantity()).isNull(),
                () -> assertThat(StockModel.of(1L, 0).getDisplayQuantity()).isZero()
            );
        }
    }

    @DisplayName("changeQuantity 로 절대값 변경 시,")
    @Nested
    class ChangeQuantity {

        @DisplayName("새 수량으로 갱신된다.")
        @Test
        void changes() {
            StockModel stock = StockModel.of(1L, 5);
            stock.changeQuantity(100);
            assertThat(stock.getQuantity()).isEqualTo(100);
        }

        @DisplayName("음수로 변경 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNegative() {
            StockModel stock = StockModel.of(1L, 5);
            CoreException result = assertThrows(CoreException.class, () -> stock.changeQuantity(-1));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
