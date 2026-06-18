package com.loopers.application.payment;

import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderTransactionService;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 결제 승인(confirm) 유스케이스 통합 테스트.
 *
 * <p>무점유 주문 → 승인 직전 점유 흐름에서 confirm 의 검증(소유자/상태/금액 위변조),
 * 점유 실패 시 승인 차단(청구 없음), 성공 확정을 검증한다.
 * FakePaymentGateway 가 항상 승인 성공을 반환하므로 승인 실패 보상 경로는
 * {@code OrderTransactionServiceIntegrationTest}에서 직접 검증한다.
 */
@SpringBootTest
class PaymentConfirmIntegrationTest {

    @Autowired private PaymentApplicationService paymentApplicationService;
    @Autowired private OrderTransactionService orderTransactionService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private StockRepository stockRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ProductModel givenProductWithStock(int stockQuantity) {
        BrandModel brand = brandRepository.save(new BrandModel("나이키", "스포츠"));
        ProductModel product = productRepository.save(
            new ProductModel(brand.getId(), "에어맥스", "러닝화", 50_000L));
        stockRepository.save(StockModel.of(product.getId(), stockQuantity));
        return product;
    }

    private OrderModel givenPendingOrder(Long userId, ProductModel product, int quantity) {
        return orderTransactionService.createPendingOrder(
            userId, List.of(new OrderItemCommand(product.getId(), quantity)), null);
    }

    private int stockOf(ProductModel product) {
        return stockRepository.findByProductId(product.getId()).orElseThrow().getQuantity();
    }

    @DisplayName("정상 confirm 시 재고가 차감되고 주문이 COMPLETED 로 확정된다.")
    @Test
    void completesOrderAndDeductsStock_whenConfirmSucceeds() {
        // arrange — 무점유 견적 (재고 10 유지)
        ProductModel product = givenProductWithStock(10);
        OrderModel pending = givenPendingOrder(1L, product, 2);
        assertThat(stockOf(product)).isEqualTo(10);

        // act — successUrl 에서 받은 paymentKey 로 승인 요청
        OrderInfo result = paymentApplicationService.confirmPayment(
            1L, "fake-payment-key", pending.getId(), pending.getTotalPrice());

        // assert — 점유(차감)는 confirm 시점에 일어난다
        assertThat(result.status()).isEqualTo(OrderStatus.COMPLETED.name());
        assertThat(stockOf(product)).isEqualTo(8);
    }

    @DisplayName("요청 금액이 주문 금액과 다르면 BAD_REQUEST — 점유/승인 없이 차단된다 (금액 위변조 방어).")
    @Test
    void rejectsConfirm_whenAmountTampered() {
        // arrange — 주문 금액 100,000원
        ProductModel product = givenProductWithStock(10);
        OrderModel pending = givenPendingOrder(1L, product, 2);

        // act — 결제창에서 조작된 금액(100원)으로 승인 시도
        CoreException result = assertThrows(CoreException.class, () ->
            paymentApplicationService.confirmPayment(1L, "fake-payment-key", pending.getId(), 100L));

        // assert — 차단 + 재고/주문 상태 불변
        OrderModel after = orderRepository.findById(pending.getId()).orElseThrow();
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        assertThat(after.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(stockOf(product)).isEqualTo(10);
    }

    @DisplayName("confirm 시점에 재고가 소진되어 있으면 승인 없이 실패한다 — 청구되지 않고 주문은 FAILED.")
    @Test
    void rejectsBeforeApproval_whenStockSoldOutAtConfirm() {
        // arrange — 재고 5: 두 유저가 3개씩 견적 (무점유라 둘 다 성공)
        ProductModel product = givenProductWithStock(5);
        OrderModel orderA = givenPendingOrder(1L, product, 3);
        OrderModel orderB = givenPendingOrder(2L, product, 3);

        // act — A 가 먼저 confirm 성공 (재고 5 → 2), B 는 점유 단계에서 거부
        paymentApplicationService.confirmPayment(1L, "key-A", orderA.getId(), orderA.getTotalPrice());
        CoreException result = assertThrows(CoreException.class, () ->
            paymentApplicationService.confirmPayment(2L, "key-B", orderB.getId(), orderB.getTotalPrice()));

        // assert — B는 승인 호출 전에 탈락 (돈 안 나감), 견적 폐기(FAILED), 재고는 A 차감분만 반영
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        assertThat(orderRepository.findById(orderB.getId()).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.FAILED);
        assertThat(stockOf(product)).isEqualTo(2);
    }

    @DisplayName("타 유저의 주문을 confirm 하면 NOT_FOUND — 존재 자체를 노출하지 않는다.")
    @Test
    void rejectsConfirm_whenNotOwner() {
        ProductModel product = givenProductWithStock(10);
        OrderModel pending = givenPendingOrder(1L, product, 1);

        CoreException result = assertThrows(CoreException.class, () ->
            paymentApplicationService.confirmPayment(2L, "fake-payment-key", pending.getId(), pending.getTotalPrice()));

        assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
    }

    @DisplayName("이미 확정된 주문을 다시 confirm 하면 BAD_REQUEST (중복 승인 방지).")
    @Test
    void rejectsConfirm_whenAlreadyCompleted() {
        // arrange — 1차 confirm 으로 COMPLETED 확정
        ProductModel product = givenProductWithStock(10);
        OrderModel pending = givenPendingOrder(1L, product, 1);
        paymentApplicationService.confirmPayment(1L, "fake-payment-key", pending.getId(), pending.getTotalPrice());

        // act — 같은 주문 재승인 시도
        CoreException result = assertThrows(CoreException.class, () ->
            paymentApplicationService.confirmPayment(1L, "fake-payment-key", pending.getId(), pending.getTotalPrice()));

        // assert — 차단 + 재고 이중 차감 없음
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        assertThat(stockOf(product)).isEqualTo(9);
    }
}
