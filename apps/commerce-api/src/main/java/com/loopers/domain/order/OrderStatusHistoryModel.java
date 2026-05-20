package com.loopers.domain.order;

import jakarta.persistence.*;

import java.time.ZonedDateTime;

@Entity
@Table(name = "order_status_histories")
public class OrderStatusHistoryModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private OrderModel order;

    @Column(name = "from_status")
    private String fromStatus;

    @Column(name = "to_status", nullable = false)
    private String toStatus;

    @Column(name = "changed_at", nullable = false)
    private ZonedDateTime changedAt;

    protected OrderStatusHistoryModel() {}

    public OrderStatusHistoryModel(OrderModel order, String fromStatus, String toStatus) {
        this.order = order;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }

    @PrePersist
    private void prePersist() {
        this.changedAt = ZonedDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public OrderModel getOrder() {
        return order;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public ZonedDateTime getChangedAt() {
        return changedAt;
    }
}
