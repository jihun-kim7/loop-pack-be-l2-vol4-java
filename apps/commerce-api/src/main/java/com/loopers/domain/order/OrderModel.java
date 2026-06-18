package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class OrderModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "original_amount"))
    private Money originalAmount;   // 쿠폰 적용 전 금액 (스냅샷)

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "discount_amount"))
    private Money discountAmount;   // 쿠폰 할인 금액 (스냅샷)

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "total_price"))
    private Money totalPrice;       // 최종 결제 금액 (= 원금 - 할인, 스냅샷)

    @Column(name = "ordered_at", nullable = false)
    private ZonedDateTime orderedAt;

    /**
     * 이 주문에 사용된 발급 쿠폰(UserCoupon) id. 쿠폰 미사용 시 null.
     *
     * <p>주문 생성(TX1)과 결제 확정/보상(TX2)이 서로 다른 요청으로 분리되어 있어,
     * 결제 실패 시 어떤 쿠폰을 복구할지 주문 스스로 알아야 한다.
     */
    @Column(name = "user_coupon_id")
    private Long userCouponId;

    /**
     * 자원 점유(재고 차감 + 쿠폰 사용)가 완료되어 결제 승인 대기를 시작한 시각.
     *
     * <p>만료 스케줄러는 PENDING 은 orderedAt 기준으로, PAYMENT_IN_PROGRESS 는
     * 이 시각 기준으로 판정한다 — 주문을 30분 전에 만들고 방금 confirm 한 건을
     * 승인 도중에 만료시키는 사고를 막기 위함.
     */
    @Column(name = "payment_started_at")
    private ZonedDateTime paymentStartedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemModel> items = new ArrayList<>();

    protected OrderModel() {}

    public OrderModel(Long userId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "userId는 필수입니다.");
        }
        this.userId = userId;
        this.status = OrderStatus.PENDING;
        this.orderedAt = ZonedDateTime.now();
    }

    public void addItem(OrderItemModel item) {
        this.items.add(item);
    }

    /** 사용된 쿠폰을 주문에 연결한다. 결제 실패 보상 시 복구 대상 식별에 사용된다. */
    public void attachUserCoupon(Long userCouponId) {
        this.userCouponId = userCouponId;
    }

    /** 총액 계산 (Money VO 반환). 도메인 내부 협력용. */
    Money calculateTotalPriceAsMoney() {
        return items.stream()
            .map(OrderItemModel::calculateSubtotalAsMoney)
            .reduce(Money.zero(), Money::plus);
    }

    /** 총액 (DTO/응답용 — Long). */
    public Long calculateTotalPrice() {
        return calculateTotalPriceAsMoney().getAmount();
    }

    /**
     * 쿠폰 미적용 기준으로 금액 스냅샷을 확정한다 (원금 = 최종, 할인 0).
     * 쿠폰 적용 시에는 이후 {@link #applyDiscount(Money)} 로 갱신한다.
     */
    public void confirmAmounts() {
        this.originalAmount = calculateTotalPriceAsMoney();
        this.discountAmount = Money.zero();
        this.totalPrice = this.originalAmount;
    }

    /**
     * 쿠폰 할인을 적용해 최종 결제 금액을 확정한다.
     *
     * <p>{@link #confirmAmounts()} 이후 호출해야 한다. 할인액이 원금을 초과하면 원금까지만
     * 할인하여 최종 금액이 음수가 되지 않도록 한다(이중 방어 — 쿠폰 도메인에서도 캡한다).
     */
    public void applyDiscount(Money discount) {
        if (this.originalAmount == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "원금 확정 후 할인을 적용할 수 있습니다.");
        }
        Money capped = this.originalAmount.isGreaterThanOrEqual(discount) ? discount : this.originalAmount;
        this.discountAmount = capped;
        this.totalPrice = this.originalAmount.minus(capped);
    }

    /**
     * 결제 승인 직전, 자원 점유 시작 (PENDING → PAYMENT_IN_PROGRESS).
     *
     * <p>이 전이가 같은 주문에 대한 중복 confirm 의 1차 방어선이다 —
     * 호출 측이 주문 행을 비관적 락으로 잡은 상태에서 호출하면, 두 번째 confirm 은
     * 락 해제 후 이 가드에서 거부된다.
     */
    public void startPayment(ZonedDateTime now) {
        if (this.status != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 대기 상태의 주문이 아닙니다.");
        }
        this.status = OrderStatus.PAYMENT_IN_PROGRESS;
        this.paymentStartedAt = now;
    }

    /** 결제 승인 성공 확정 (PAYMENT_IN_PROGRESS → COMPLETED). */
    public void complete() {
        if (this.status != OrderStatus.PAYMENT_IN_PROGRESS) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 진행 중인 주문만 완료 처리할 수 있습니다.");
        }
        this.status = OrderStatus.COMPLETED;
    }

    /**
     * 주문 실패 처리 (PENDING/PAYMENT_IN_PROGRESS → FAILED).
     *
     * <p>PENDING 에서: 만료/점유 실패 — 점유한 자원이 없으므로 상태만 닫는다.
     * PAYMENT_IN_PROGRESS 에서: 승인 실패/미결제 확인 — 재고/쿠폰 복구는 보상 트랜잭션이 별도로 수행한다.
     */
    public void fail() {
        if (this.status != OrderStatus.PENDING && this.status != OrderStatus.PAYMENT_IN_PROGRESS) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 확정된 주문은 실패 처리할 수 없습니다.");
        }
        this.status = OrderStatus.FAILED;
    }

    public Long getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    /** 최종 결제 금액 (DTO/응답용). null 가능 (확정 전). */
    public Long getTotalPrice() {
        return totalPrice == null ? null : totalPrice.getAmount();
    }

    /** 쿠폰 적용 전 원금 (DTO/응답용). null 가능 (확정 전). */
    public Long getOriginalAmount() {
        return originalAmount == null ? null : originalAmount.getAmount();
    }

    /** 쿠폰 할인 금액 (DTO/응답용). null 가능 (확정 전). */
    public Long getDiscountAmount() {
        return discountAmount == null ? null : discountAmount.getAmount();
    }

    public ZonedDateTime getOrderedAt() {
        return orderedAt;
    }

    /** 사용된 발급 쿠폰 id. 쿠폰 미사용 주문이면 null. */
    public Long getUserCouponId() {
        return userCouponId;
    }

    /** 자원 점유 시작 시각. PAYMENT_IN_PROGRESS 진입 전이면 null. */
    public ZonedDateTime getPaymentStartedAt() {
        return paymentStartedAt;
    }

    public List<OrderItemModel> getItems() {
        return items;
    }
}
