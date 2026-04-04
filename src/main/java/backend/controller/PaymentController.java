package backend.controller;

import backend.dto.common.ApiResponse;
import backend.service.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "API cho thanh toán PayOS")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create-link/{orderId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Tạo link thanh toán PayOS", description = "Tạo url chuyển trang sau khi checkout")
    public ResponseEntity<ApiResponse<String>> createPaymentLink(@PathVariable UUID orderId) {
        String checkoutUrl = paymentService.createPaymentLink(orderId);
        return ResponseEntity.ok(ApiResponse.success(checkoutUrl, "Payment link created successfully"));
    }

    @PostMapping("/payos-webhook")
    @Operation(summary = "PayOS Webhook", description = "Nhận callback từ PayOS khi giao dịch có biến động trạng thái")
    public ResponseEntity<ApiResponse<String>> payosWebhook(@RequestBody JsonNode body) {
        paymentService.verifyWebhook(body);
        return ResponseEntity.ok(ApiResponse.success("Webhook received"));
    }
}
