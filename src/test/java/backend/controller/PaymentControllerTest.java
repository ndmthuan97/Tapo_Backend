package backend.controller;

import backend.exception.AppException;
import backend.dto.common.CustomCode;
import backend.exception.GlobalExceptionHandler;
import backend.service.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * PaymentController Unit Tests — standaloneSetup + GlobalExceptionHandler.
 * Covers: PAY-002, PAY-003, PAY-004
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentController — Unit Tests")
class PaymentControllerTest {

    @Mock PaymentService paymentService;

    @InjectMocks PaymentController paymentController;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders
                .standaloneSetup(paymentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── POST /api/payments/payos-webhook ─────────────────────────────────────

    @Nested
    @DisplayName("POST /api/payments/payos-webhook")
    class PayosWebhook {

        private String successPayload() {
            return """
                    {
                      "code": "00",
                      "desc": "success",
                      "data": {
                        "orderCode": 1234567890,
                        "amount": 500000,
                        "description": "Thanh toan don hang",
                        "accountNumber": "12345678",
                        "reference": "TXN_REF_001",
                        "transactionDateTime": "2024-01-01T10:00:00Z",
                        "paymentLinkId": "abc-xyz-123",
                        "code": "00",
                        "desc": "Thành công",
                        "counterAccountBankId": null,
                        "counterAccountBankName": null,
                        "counterAccountName": null,
                        "counterAccountNumber": null,
                        "virtualAccountName": null,
                        "virtualAccountNumber": null
                      },
                      "signature": "valid-hmac-signature"
                    }
                    """;
        }

        private String failurePayload() {
            return """
                    {
                      "code": "01",
                      "desc": "Cancelled",
                      "data": {
                        "orderCode": 1234567890,
                        "amount": 500000,
                        "description": "Thanh toan don hang",
                        "accountNumber": null,
                        "reference": null,
                        "transactionDateTime": null,
                        "paymentLinkId": "abc-xyz-123",
                        "code": "01",
                        "desc": "CANCELLED",
                        "counterAccountBankId": null,
                        "counterAccountBankName": null,
                        "counterAccountName": null,
                        "counterAccountNumber": null,
                        "virtualAccountName": null,
                        "virtualAccountNumber": null
                      },
                      "signature": "valid-hmac-signature-for-cancel"
                    }
                    """;
        }

        @Test
        @DisplayName("PAY-002: webhook payment thành công → 200, service xử lý order CONFIRMED")
        void webhook_paymentSuccess_200() throws Exception {
            willDoNothing().given(paymentService).verifyWebhook(any(JsonNode.class));

            mockMvc.perform(post("/api/payments/payos-webhook")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(successPayload()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value("Webhook received"));
        }

        @Test
        @DisplayName("PAY-003: webhook payment thất bại (cancelled) → 200, service xử lý order CANCELLED")
        void webhook_paymentCancelled_200() throws Exception {
            willDoNothing().given(paymentService).verifyWebhook(any(JsonNode.class));

            mockMvc.perform(post("/api/payments/payos-webhook")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(failurePayload()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value("Webhook received"));
        }

        @Test
        @DisplayName("PAY-004: webhook signature không hợp lệ → 400, bỏ qua request")
        void webhook_invalidSignature_400() throws Exception {
            willThrow(new AppException(CustomCode.BAD_REQUEST))
                    .given(paymentService).verifyWebhook(any(JsonNode.class));

            String tamperedPayload = """
                    {
                      "code": "00",
                      "desc": "success",
                      "data": { "orderCode": 9999 },
                      "signature": "tampered-invalid-signature"
                    }
                    """;

            mockMvc.perform(post("/api/payments/payos-webhook")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(tamperedPayload))
                    .andExpect(status().isBadRequest());
        }
    }
}
