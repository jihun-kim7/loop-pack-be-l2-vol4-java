package com.loopers.domain.like;

import jakarta.persistence.*;

import java.time.ZonedDateTime;

@Entity
@Table(name = "likes")
public class LikeModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    protected LikeModel() {}

    public LikeModel(Long userId, Long productId) {
        this.userId = userId;
        this.productId = productId;
    }

    public static LikeModel of(Long userId, Long productId) {
        return new LikeModel(userId, productId);
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = ZonedDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getProductId() {
        return productId;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }
}
