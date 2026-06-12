package com.loopers.infrastructure.stock;

import com.loopers.domain.stock.StockModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface StockJpaRepository extends JpaRepository<StockModel, Long> {
    Optional<StockModel> findByProductId(Long productId);
    List<StockModel> findAllByProductIdIn(List<Long> productIds);

    /**
     * 조건부 원자 차감.
     *
     * <p>벌크 쿼리는 JPA 영속성 메커니즘을 우회하므로 두 가지를 수동으로 처리한다:
     * <ul>
     *   <li>{@code version = version + 1} — 어드민 절대값 수정 경로의 낙관적 락(@Version)이
     *       동시 차감을 감지할 수 있도록 버전을 직접 증가시킨다. 이게 없으면 어드민 커밋이
     *       차감분을 덮어써 Lost Update 가 발생한다.</li>
     *   <li>{@code updatedAt} — {@code @PreUpdate} 콜백이 동작하지 않으므로 직접 세팅한다.</li>
     * </ul>
     */
    @Modifying
    @Query("UPDATE StockModel s SET s.quantity.value = s.quantity.value - :qty, "
        + "s.version = s.version + 1, s.updatedAt = :now "
        + "WHERE s.productId = :productId AND s.quantity.value >= :qty")
    int deduct(@Param("productId") Long productId, @Param("qty") int qty, @Param("now") ZonedDateTime now);

    /** 원자 복구 — 차감의 역연산. version/updatedAt 수동 처리 사유는 {@link #deduct} 참고. */
    @Modifying
    @Query("UPDATE StockModel s SET s.quantity.value = s.quantity.value + :qty, "
        + "s.version = s.version + 1, s.updatedAt = :now "
        + "WHERE s.productId = :productId")
    int restore(@Param("productId") Long productId, @Param("qty") int qty, @Param("now") ZonedDateTime now);
}
