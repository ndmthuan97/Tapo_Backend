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

    @Value("${app.base-url:http://localhost:5173}")
    private String appBaseUrl;

    @Override
    @Transactional
    public String createPaymentLink(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(CustomCode.ORDER_NOT_FOUND));

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new AppException(CustomCode.PAYMENT_ALREADY_COMPLETED);
        }

        // Tạo mã đơn hàng số nguyên ngẫu nhiên cho PayOS (Payos yêu cầu mã đơn <= 9007199254740991)
        long payosOrderCode = System.currentTimeMillis() % 10000000000L;

        String productName = "Thanh toán giao dịch " + order.getOrderCode();
        int amount = order.getTotalAmount().intValue();
        // ItemData 
        ItemData item = ItemData.builder()
                .name(productName)
                .quantity(1)
                .price(amount)
                .build();

        String returnUrl = appBaseUrl + "/orders/" + order.getId() + "?payment=success";
        String cancelUrl = appBaseUrl + "/orders/" + order.getId() + "?payment=fail";

        PaymentData paymentData = PaymentData.builder()
                .orderCode(payosOrderCode)
                .amount(amount)
                .description("Thanh toan DHT " + payosOrderCode)
                .returnUrl(returnUrl)
                .cancelUrl(cancelUrl)
                .item(item)
                .build();

        try {
            CheckoutResponseData data = payOS.createPaymentLink(paymentData);
            
            // Lưu lại Payment entity
            Payment payment = paymentRepository.findByOrderId(orderId).orElse(new Payment());
            payment.setOrder(order);
            payment.setPayosOrderCode(payosOrderCode);
            payment.setAmount(order.getTotalAmount());
            payment.setStatus(PaymentStatus.UNPAID);
            payment.setPayosPaymentLinkId(data.getPaymentLinkId());
            payment.setPaymentLinkUrl(data.getCheckoutUrl());
            paymentRepository.save(payment);

            return data.getCheckoutUrl();
        } catch (Exception e) {
            log.error("PayOS create link failed", e);
            throw new RuntimeException("Không thể tạo link thanh toán PayOS: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void verifyWebhook(JsonNode webhookBody) {
        try {
            WebhookData data = payOS.verifyPaymentWebhookData(objectMapper.treeToValue(webhookBody, Webhook.class));
            log.info("Webhook Verify Result: Order code {} - amount {}", data.getOrderCode(), data.getAmount());

            if ("00".equals(data.getCode()) || "success".equalsIgnoreCase(data.getCode())) {
                Payment payment = paymentRepository.findByPayosOrderCode(data.getOrderCode())
                        .orElseThrow(() -> new RuntimeException("Payment not found for order code: " + data.getOrderCode()));

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
            }
        } catch (Exception e) {
            log.error("PayOS webhook verification failed: ", e);
        }
    }
}
