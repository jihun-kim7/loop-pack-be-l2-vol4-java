package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;

@Entity
@Table(name = "order_items")
public class OrderItemModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private OrderModel order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "product_price")
    private Long productPrice;

    @Column(name = "quantity")
    private int quantity;

    @Column(name = "discount_amount", nullable = false)
    private Long discountAmount = 0L;

    protected OrderItemModel() {}

    public OrderItemModel(OrderModel order, Long productId, String productName, Long productPrice, int quantity) {
        if (quantity < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1 이상이어야 합니다.");
        }
        this.order = order;
        this.productId = productId;
        this.productName = productName;
        this.productPrice = productPrice;
        this.quantity = quantity;
        this.discountAmount = 0L;
    }

    public Long calculateSubtotal() {
        return (long) productPrice * quantity;
    }

    public Long getId() {
        return id;
    }

    public OrderModel getOrder() {
        return order;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public Long getProductPrice() {
        return productPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public Long getDiscountAmount() {
        return discountAmount;
    }
}
