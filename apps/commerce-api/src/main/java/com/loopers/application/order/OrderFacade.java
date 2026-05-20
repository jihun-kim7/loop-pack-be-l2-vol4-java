package com.loopers.application.order;

import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderService orderService;
    private final UserService userService;

    public OrderInfo createOrder(String loginId, List<OrderItemCommand> items) {
        UserModel user = userService.getUser(loginId);
        OrderModel order = orderService.createOrder(user.getId(), items);
        return OrderInfo.from(order);
    }

    public List<OrderInfo> getOrders(String loginId, ZonedDateTime startAt, ZonedDateTime endAt) {
        UserModel user = userService.getUser(loginId);
        return orderService.getOrders(user.getId(), startAt, endAt).stream()
            .map(OrderInfo::from)
            .toList();
    }

    public OrderInfo getOrder(String loginId, Long orderId) {
        UserModel user = userService.getUser(loginId);
        OrderModel order = orderService.getOrder(user.getId(), orderId);
        return OrderInfo.from(order);
    }

    public List<OrderInfo> getAllOrders(int page, int size) {
        return orderService.getAllOrders(page, size).stream()
            .map(OrderInfo::from)
            .toList();
    }

    public OrderInfo getOrderAdmin(Long orderId) {
        OrderModel order = orderService.findById(orderId);
        return OrderInfo.from(order);
    }
}
