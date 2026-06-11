package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.ZonedDateTime;

/**
 * 발급된 사용자 쿠폰 (실제 주문에 사용되는 인스턴스).
 *
 * <p><strong>동시성 — 낙관적 락(@Version)</strong>:
 * 동일 쿠폰으로 여러 기기에서 동시에 주문해도 단 한 번만 사용되어야 한다.
 * "경쟁자 중 1명만 성공" 설계에는 낙관적 락이 자연스럽다. 두 트랜잭션이 같은 version 을 읽고
 * USED 로 변경하면, 커밋 시 한 쪽만 성공하고 다른 쪽은 {@code OptimisticLockException} 으로 실패한다.
 *
 * <p><strong>만료 스냅샷</strong>: 발급 시점 템플릿의 만료 시각을 복사해 둔다.
 * 발급분의 유효기간을 자체적으로 판단할 수 있어, 사용/조회 시 템플릿 재조회 없이 만료를 결정한다.
 *
 * <p>{@code (user_id, coupon_id)} 복합 UK 로 동일 템플릿 중복 발급을 DB 레벨에서 방지한다.
 */
@Entity
@Table(name = "user_coupons", uniqueConstraints = {
    @UniqueConstraint(name = "uk_user_coupons_user_coupon", columnNames = {"user_id", "coupon_id"})
})
public class UserCouponModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponStatus status;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    @Version
    private Long version;

    protected UserCouponModel() {}

    public UserCouponModel(Long userId, Long couponId, ZonedDateTime expiredAt) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "userId는 필수입니다.");
        }
        if (couponId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "couponId는 필수입니다.");
        }
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료 시각은 필수입니다.");
        }
        this.userId = userId;
        this.couponId = couponId;
        this.expiredAt = expiredAt;
        this.status = CouponStatus.AVAILABLE;
    }

    public static UserCouponModel issue(Long userId, CouponModel coupon) {
        return new UserCouponModel(userId, coupon.getId(), coupon.getExpiredAt());
    }

    /**
     * 쿠폰을 사용 처리한다 (AVAILABLE → USED).
     *
     * <p>만료/최소주문금액 검증은 {@link CouponModel#validateApplicable}이 책임진다.
     * 여기서는 중복 사용(status) 검증과 상태 전이만 수행한다.
     * 동시 사용 충돌(같은 쿠폰 동시 주문)은 {@code @Version} 낙관적 락이 커밋 시점에 한 건만 통과시킨다.
     */
    public void use(ZonedDateTime now) {
        if (this.status == CouponStatus.USED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 사용된 쿠폰입니다.");
        }
        this.status = CouponStatus.USED;
        this.usedAt = now;
    }

    /**
     * 사용 처리를 되돌린다 (USED → AVAILABLE). 결제 실패 보상 트랜잭션 전용.
     *
     * <p>소프트 삭제 복원({@code BaseEntity#restore})과는 무관하다.
     */
    public void cancelUse() {
        if (this.status != CouponStatus.USED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용된 쿠폰만 사용 취소할 수 있습니다.");
        }
        this.status = CouponStatus.AVAILABLE;
        this.usedAt = null;
    }

    public boolean isExpired(ZonedDateTime now) {
        return expiredAt.isBefore(now);
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    /**
     * 조회 시점 기준 표시 상태. USED 가 아니면서 만료 시각이 지났으면 EXPIRED 로 보여준다.
     * (저장 상태는 AVAILABLE/USED 만 가지며 EXPIRED 는 파생값)
     */
    public CouponStatus displayStatus(ZonedDateTime now) {
        if (this.status == CouponStatus.USED) {
            return CouponStatus.USED;
        }
        return isExpired(now) ? CouponStatus.EXPIRED : CouponStatus.AVAILABLE;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCouponId() {
        return couponId;
    }

    public CouponStatus getStatus() {
        return status;
    }

    public ZonedDateTime getExpiredAt() {
        return expiredAt;
    }

    public ZonedDateTime getUsedAt() {
        return usedAt;
    }


}
