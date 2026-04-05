package backend.dto.notification;

import java.time.Instant;

/**
 * DTO pushed to admin WebSocket subscribers — java-pro: Java 21 record, immutable.
 *
 * <p>JSON structure sent to {@code /topic/admin/notifications}:
 * <pre>
 * {
 *   "type":      "NEW_ORDER",
 *   "title":     "Đơn hàng mới",
 *   "message":   "#TP2025001 — Nguyễn Văn A — 1.500.000₫",
 *   "timestamp": "2025-04-05T10:30:00Z",
 *   "meta":      { "orderCode": "TP2025001" }
 * }
 * </pre>
 */
public record AdminNotificationDto(
        NotificationType type,
        String title,
        String message,
        Instant timestamp,
        java.util.Map<String, Object> meta
) {
    public enum NotificationType {
        NEW_ORDER,
        NEW_RETURN_REQUEST,
        NEW_CONTACT_MESSAGE
    }

    // ── Factory helpers (KISS — no builder overhead for simple records) ──────────

    public static AdminNotificationDto newOrder(String orderCode, String customerName, long totalAmount) {
        return new AdminNotificationDto(
                NotificationType.NEW_ORDER,
                "Đơn hàng mới",
                String.format("#%s — %s — %,d₫", orderCode, customerName, totalAmount),
                Instant.now(),
                java.util.Map.of("orderCode", orderCode)
        );
    }

    public static AdminNotificationDto newReturnRequest(String orderCode, String customerName) {
        return new AdminNotificationDto(
                NotificationType.NEW_RETURN_REQUEST,
                "Yêu cầu đổi/trả",
                String.format("Đơn #%s — %s vừa gửi yêu cầu đổi/trả", orderCode, customerName),
                Instant.now(),
                java.util.Map.of("orderCode", orderCode)
        );
    }

    public static AdminNotificationDto newContactMessage(String senderName, String topic) {
        return new AdminNotificationDto(
                NotificationType.NEW_CONTACT_MESSAGE,
                "Tin nhắn mới",
                String.format("%s gửi: %s", senderName, topic),
                Instant.now(),
                java.util.Map.of("topic", topic)
        );
    }
}
