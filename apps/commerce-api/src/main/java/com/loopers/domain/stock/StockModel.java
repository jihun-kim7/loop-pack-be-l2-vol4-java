package com.loopers.domain.stock;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;

import java.time.ZonedDateTime;

@Entity
@Table(name = "stocks")
public class StockModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", unique = true, nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    @Version
    private Long version;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    protected StockModel() {}

    public StockModel(Long productId, int quantity) {
        if (quantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
        this.productId = productId;
        this.quantity = quantity;
    }

    public static StockModel of(Long productId, int quantity) {
        return new StockModel(productId, quantity);
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }

    public void deduct(int quantity) {
        if (!hasEnough(quantity)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
        }
        this.quantity -= quantity;
    }

    public boolean hasEnough(int quantity) {
        return this.quantity >= quantity;
    }

    public boolean isAvailable() {
        return this.quantity > 0;
    }

    public Integer getDisplayQuantity() {
        if (this.quantity <= 10) {
            return this.quantity;
        }
        return null;
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public Long getVersion() {
        return version;
    }
}
