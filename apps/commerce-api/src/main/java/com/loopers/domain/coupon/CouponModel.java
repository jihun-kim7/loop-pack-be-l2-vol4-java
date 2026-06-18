package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/**
 * 쿠폰 템플릿 (어드민이 관리하는 발급 원본).
 *
 * <p>실제 사용자가 보유·사용하는 것은 {@link UserCouponModel}(발급분)이며,
 * 이 템플릿은 할인 정책(타입/값/최소주문금액/만료시각)만 정의한다.
 * 발급 시 혜택이 발급분으로 스냅샷되므로, 템플릿 수정은 이후 발급분에만 영향을 준다 —
 * 검증/할인 계산은 발급분({@code UserCouponModel.validateApplicable/calculateDiscount})이 수행한다.
 */
@Entity
@Table(name = "coupons")
public class CouponModel extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponType type;

    /** 할인 값. FIXED=할인금액(원), RATE=할인율(%). */
    @Column(nullable = false)
    private long value;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "min_order_amount", nullable = false))
    private Money minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    protected CouponModel() {}

    public CouponModel(String name, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        validateFields(name, type, value, expiredAt);
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = Money.of(minOrderAmount == null ? 0L : minOrderAmount);   // 미지정 시 최소금액 조건 없음
        this.expiredAt = expiredAt;
    }

    public void update(String name, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        validateFields(name, type, value, expiredAt);
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = Money.of(minOrderAmount == null ? 0L : minOrderAmount);
        this.expiredAt = expiredAt;
    }

    private void validateFields(String name, CouponType type, long value, ZonedDateTime expiredAt) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 비어있을 수 없습니다.");
        }
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 타입은 필수입니다.");
        }
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료 시각은 필수입니다.");
        }
        type.validateValue(value);
    }

    public boolean isExpired(ZonedDateTime now) {
        return expiredAt.isBefore(now);
    }

    public String getName() {
        return name;
    }

    public CouponType getType() {
        return type;
    }

    public long getValue() {
        return value;
    }

    public Long getMinOrderAmount() {
        return minOrderAmount.getAmount();
    }

    public ZonedDateTime getExpiredAt() {
        return expiredAt;
    }
}
