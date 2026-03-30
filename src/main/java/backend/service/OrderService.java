package backend.service;

import backend.dto.order.CreateOrderRequest;
import backend.dto.order.OrderDto;
import backend.dto.order.OrderSummary;
import backend.model.enums.OrderStatus;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface OrderService {

    OrderDto createOrder(UUID userId, CreateOrderRequest request);

    Page<OrderSummary> getMyOrders(UUID userId, int page, int size, OrderStatus status);

    OrderDto getOrderDetail(UUID userId, UUID orderId);

    OrderDto cancelOrder(UUID userId, UUID orderId);

    /** Admin: all orders */
    Page<OrderSummary> getAllOrders(int page, int size, OrderStatus status);

    /** Admin: change order status */
    OrderDto updateOrderStatus(UUID orderId, OrderStatus newStatus, String note);
}
