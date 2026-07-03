package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponModel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CouponJpaRepository extends JpaRepository<CouponModel, Long> {

    @Query("SELECT c FROM CouponModel c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<CouponModel> findByIdAndDeletedAtIsNull(@Param("id") Long id);

    @Query("SELECT c FROM CouponModel c WHERE c.deletedAt IS NULL ORDER BY c.createdAt DESC")
    List<CouponModel> findAllByDeletedAtIsNull(Pageable pageable);

    /** 선착순 수량 조건부 원자 증가 — 무제한(null)이면 항상, 아니면 잔여가 있을 때만 +1. 0행 = 매진. */
    @Modifying
    @Query("UPDATE CouponModel c SET c.issuedCount = c.issuedCount + 1 "
        + "WHERE c.id = :id AND c.deletedAt IS NULL "
        + "AND (c.totalQuantity IS NULL OR c.issuedCount < c.totalQuantity)")
    int tryIncreaseIssuedCount(@Param("id") Long id);
}
