package com.loopers.domain.order;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;

    @Transactional
    public OrderModel createOrder(Long userId, List<OrderItemCommand> items) {
        OrderModel order = new OrderModel(userId);

        for (OrderItemCommand itemCommand : items) {
            ProductModel product = productRepository.findById(itemCommand.productId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + itemCommand.productId() + "] 상품을 찾을 수 없습니다."));

            StockModel stock = stockRepository.findByProductId(product.getId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "재고 정보를 찾을 수 없습니다."));
            stock.deduct(itemCommand.quantity());
            stockRepository.save(stock);

            OrderItemModel orderItem = new OrderItemModel(
                order,
                product.getId(),
                product.getName(),
                product.getPrice(),
                itemCommand.quantity()
            );
            order.addItem(orderItem);
        }

        order.confirmTotalPrice();
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public List<OrderModel> getOrders(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        return orderRepository.findByUserIdAndOrderedAtBetween(userId, startAt, endAt);
    }

    @Transactional(readOnly = true)
    public OrderModel getOrder(Long userId, Long orderId) {
        OrderModel order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다."));

        if (!order.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다.");
        }
        return order;
    }

    @Transactional(readOnly = true)
    public OrderModel findById(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<OrderModel> getAllOrders(int page, int size) {
        return orderRepository.findAll(page, size);
    }
}
