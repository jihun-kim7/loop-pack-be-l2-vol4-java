package com.loopers.domain.stock;

import com.loopers.domain.common.Quantity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.ZonedDateTime;

@Entity
@Table(name = "stocks")
public class StockModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", unique = true, nullable = false)
    private Long productId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "quantity", nullable = false))
    private Quantity quantity;

    /**
     * 낙관적 락 버전 필드 — 어드민 직접 수정 경로({@link #changeQuantity}) 보호용.
     *
     * <p>주문의 재고 차감/복구는 조건부 원자 UPDATE(벌크 쿼리)로 수행되며,
     * 벌크 쿼리는 JPA 버전 관리를 우회하므로 쿼리에서 {@code version = version + 1}을
     * 수동으로 증가시킨다. 덕분에 어드민이 절대값 수정 중 동시 주문이 차감하면
     * 어드민의 커밋이 버전 충돌로 거부되어 차감분 유실(Lost Update)을 막는다.
     */
    @Version
    private Long version;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    protected StockModel() {}

    public StockModel(Long productId, int quantity) {
        this.productId = productId;
        this.quantity = Quantity.of(quantity);   // 음수 검증은 VO 내부에서
    }

    public static StockModel of(Long productId, int quantity) {
        return new StockModel(productId, quantity);
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }

    /**
     * 재고 수량을 절대값으로 설정한다. 어드민이 상품 수정 시 재고를 직접 조정하는 용도.
     */
    public void changeQuantity(int newQuantity) {
        this.quantity = Quantity.of(newQuantity);  // 음수 검증 VO 내부
    }

    public boolean hasEnough(int qty) {
        return this.quantity.isGreaterThanOrEqual(Quantity.of(qty));
    }

    public boolean isAvailable() {
        return this.quantity.isPositive();
    }

    public Integer getDisplayQuantity() {
        int value = this.quantity.getValue();
        if (value <= 10) {
            return value;
        }
        return null;
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    /** 수량 (DTO/응답용 — int). 도메인 내부에서는 {@link Quantity} 로 캡슐화되어 있다. */
    public int getQuantity() {
        return quantity.getValue();
    }

}
