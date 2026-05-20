package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;

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

    @Column(name = "total_price")
    private Long totalPrice;

    @Column(name = "ordered_at", nullable = false)
    private ZonedDateTime orderedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemModel> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderStatusHistoryModel> statusHistories = new ArrayList<>();

    protected OrderModel() {}

    public OrderModel(Long userId) {
        this.userId = userId;
        this.status = OrderStatus.PENDING;
        this.orderedAt = ZonedDateTime.now();
        this.statusHistories.add(new OrderStatusHistoryModel(this, null, OrderStatus.PENDING.name()));
    }

    public void addItem(OrderItemModel item) {
        this.items.add(item);
    }

    public Long calculateTotalPrice() {
        return items.stream().mapToLong(OrderItemModel::calculateSubtotal).sum();
    }

    public void confirmTotalPrice() {
        this.totalPrice = calculateTotalPrice();
    }

    public void complete() {
        if (this.status != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "대기 중인 주문만 완료 처리할 수 있습니다.");
        }
        String from = this.status.name();
        this.status = OrderStatus.COMPLETED;
        this.statusHistories.add(new OrderStatusHistoryModel(this, from, OrderStatus.COMPLETED.name()));
    }

    public void cancel() {
        if (this.status != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "대기 중인 주문만 취소할 수 있습니다.");
        }
        String from = this.status.name();
        this.status = OrderStatus.CANCELLED;
        this.statusHistories.add(new OrderStatusHistoryModel(this, from, OrderStatus.CANCELLED.name()));
    }

    public Long getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Long getTotalPrice() {
        return totalPrice;
    }

    public ZonedDateTime getOrderedAt() {
        return orderedAt;
    }

    public List<OrderItemModel> getItems() {
        return items;
    }

    public List<OrderStatusHistoryModel> getStatusHistories() {
        return statusHistories;
    }
}
