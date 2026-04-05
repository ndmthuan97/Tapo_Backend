package backend.controller;

import backend.dto.common.ApiResponse;
import backend.dto.order.CreateOrderRequest;
import backend.dto.order.OrderDto;
import backend.dto.order.OrderSummary;
import backend.model.enums.OrderStatus;
import backend.security.CustomUserDetails;
import backend.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // ── Customer endpoints ──────────────────────────────────────────────────────

    @PostMapping("/api/orders")
    public ResponseEntity<ApiResponse<OrderDto>> createOrder(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đặt hàng thành công",
                        orderService.createOrder(principal.getId(), request)));
    }

    @GetMapping("/api/orders")
    public ResponseEntity<ApiResponse<Page<OrderSummary>>> getMyOrders(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(defaultValue = "0")    int page,
            @RequestParam(defaultValue = "10")   int size,
            @RequestParam(required = false)      OrderStatus status
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.getMyOrders(principal.getId(), page, size, status)
        ));
    }

    @GetMapping("/api/orders/{id}")
    public ResponseEntity<ApiResponse<OrderDto>> getOrderDetail(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.getOrderDetail(principal.getId(), id)
        ));
    }

    @PutMapping("/api/orders/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderDto>> cancelOrder(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResponse.success("Đã hủy đơn hàng",
                orderService.cancelOrder(principal.getId(), id)));
    }

    // ── Admin endpoints ─────────────────────────────────────────────────────────

    @GetMapping("/api/admin/orders")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_STAFF')")
    public ResponseEntity<ApiResponse<Page<OrderSummary>>> getAllOrders(
            @RequestParam(defaultValue = "0")    int page,
            @RequestParam(defaultValue = "20")   int size,
            @RequestParam(required = false)      OrderStatus status
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.getAllOrders(page, size, status)
        ));
    }

    @PutMapping("/api/admin/orders/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_STAFF')")
    public ResponseEntity<ApiResponse<OrderDto>> updateOrderStatus(
            @PathVariable UUID id,
            @RequestParam OrderStatus status,
            @RequestParam(required = false) String note
    ) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái thành công",
                orderService.updateOrderStatus(id, status, note)));
    }

    @GetMapping("/api/admin/orders/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_STAFF')")
    public ResponseEntity<ApiResponse<OrderDto>> adminGetOrderDetail(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.adminGetOrderDetail(id)));
    }
}
