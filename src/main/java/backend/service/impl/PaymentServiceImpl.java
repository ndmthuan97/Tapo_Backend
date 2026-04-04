package backend.service.impl;

import backend.dto.common.CustomCode;
import backend.exception.AppException;
import backend.model.entity.Order;
import backend.model.entity.Payment;
import backend.model.enums.OrderStatus;
import backend.model.enums.PaymentStatus;
import backend.repository.OrderRepository;
import backend.repository.PaymentRepository;
import backend.service.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.PayOS;
import vn.payos.type.CheckoutResponseData;
import vn.payos.type.ItemData;
import vn.payos.type.PaymentData;
import vn.payos.type.WebhookData;
import vn.payos.type.Webhook;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PayOS payOS;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;

    // SecureRandom thread-safe, singleton để tránh re-seeding overhead
    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${app.base-url:http://localhost:5173}")
    private String appBaseUrl;

    // ── Create Payment Link ────────────────────────────────────────────────────

    @Override
    @Transactional
    public String createPaymentLink(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(CustomCode.ORDER_NOT_FOUND));

        // Idempotency: trả lại link cũ nếu đã tồn tại và chưa thanh toán
        var existingOpt = paymentRepository.findByOrderId(orderId);
        if (existingOpt.isPresent()) {
            Payment existing = existingOpt.get();
            if (existing.getStatus() == PaymentStatus.PAID) {
                throw new AppException(CustomCode.PAYMENT_ALREADY_COMPLETED);
            }
            if (existing.getPaymentLinkUrl() != null) {
                log.info("Returning existing payment link for order {}", orderId);
                return existing.getPaymentLinkUrl();
            }
        }

        // BUG FIX: System.currentTimeMillis() % 10^10 dễ collision khi load cao
        // → SecureRandom đảm bảo unique trong range PayOS (1..9_999_999_999)
        long payosOrderCode = (Math.abs(RANDOM.nextLong()) % 9_999_999_999L) + 1;

        // BUG FIX: PayOS giới hạn description <= 25 ký tự
        String description = truncate("DHT " + order.getOrderCode(), 25);

        int amount = order.getTotalAmount().intValue();
        ItemData item = ItemData.builder()
                .name(truncate("Thanh toan don hang " + order.getOrderCode(), 100))
                .quantity(1)
                .price(amount)
                .build();

        String returnUrl  = appBaseUrl + "/orders/" + order.getId() + "?payment=success";
        String cancelUrl  = appBaseUrl + "/orders/" + order.getId() + "?payment=cancelled";

        PaymentData paymentData = PaymentData.builder()
                .orderCode(payosOrderCode)
                .amount(amount)
                .description(description)
                .returnUrl(returnUrl)
                .cancelUrl(cancelUrl)
                .item(item)
                .build();

        try {
            CheckoutResponseData data = payOS.createPaymentLink(paymentData);

            // Upsert: tạo mới hoặc cập nhật Payment entity hiện có
            Payment payment = existingOpt.orElse(new Payment());
            payment.setOrder(order);
            payment.setPayosOrderCode(payosOrderCode);
            payment.setAmount(order.getTotalAmount());
            payment.setStatus(PaymentStatus.UNPAID);
            payment.setPayosPaymentLinkId(data.getPaymentLinkId());
            payment.setPaymentLinkUrl(data.getCheckoutUrl());
            paymentRepository.save(payment);

            log.info("Created PayOS payment link for order {} → code {}", orderId, payosOrderCode);
            return data.getCheckoutUrl();

        } catch (Exception e) {
            log.error("PayOS create link failed for order {}: {}", orderId, e.getMessage(), e);
            // Throw RuntimeException → GlobalExceptionHandler bắt và trả HTTP 422
            throw new RuntimeException("Không thể tạo link thanh toán PayOS: " + e.getMessage());
        }
    }

    // ── Verify Webhook ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void verifyWebhook(JsonNode webhookBody) {
        WebhookData data;
        try {
            data = payOS.verifyPaymentWebhookData(
                    objectMapper.treeToValue(webhookBody, Webhook.class));
        } catch (Exception e) {
            // BUG FIX: không swallow exception → propagate để controller trả 422
            log.error("PayOS webhook signature verification failed: {}", e.getMessage());
            throw new RuntimeException("Webhook verification failed: " + e.getMessage());
        }

        log.info("Webhook verified OK: orderCode={}, code={}, desc={}, amount={}",
                data.getOrderCode(), data.getCode(), data.getDesc(), data.getAmount());

        // PayOS trả "00" trong `code` khi thành công
        boolean isSuccess = "00".equals(data.getCode())
                || "success".equalsIgnoreCase(data.getDesc());

        if (!isSuccess) {
            log.warn("Webhook non-success: code={}, desc={}", data.getCode(), data.getDesc());
            return; // Giao dịch fail/cancel — không cần xử lý
        }

        Payment payment = paymentRepository.findByPayosOrderCode(data.getOrderCode())
                .orElseThrow(() -> {
                    log.error("Payment not found for payosOrderCode: {}", data.getOrderCode());
                    return new AppException(CustomCode.PAYMENT_NOT_FOUND);
                });

        // Idempotency: bỏ qua webhook duplicate
        if (payment.getStatus() == PaymentStatus.PAID) {
            log.info("Duplicate webhook ignored for already-paid orderCode {}", data.getOrderCode());
            return;
        }

        payment.setStatus(PaymentStatus.PAID);
        payment.setTransactionId(data.getReference());
        payment.setPaidAt(Instant.now());
        paymentRepository.save(payment);

        Order order = payment.getOrder();
        order.setPaymentStatus(PaymentStatus.PAID);
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.CONFIRMED);
        }
        orderRepository.save(order);

        log.info("Order {} marked PAID via PayOS webhook (txn: {})", order.getId(), data.getReference());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String truncate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
