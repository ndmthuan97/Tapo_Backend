package backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public interface PaymentService {
    String createPaymentLink(UUID orderId);
    void verifyWebhook(JsonNode webhookBody);
}
