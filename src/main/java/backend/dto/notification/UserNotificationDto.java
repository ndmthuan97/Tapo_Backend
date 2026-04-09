package backend.dto.notification;

import java.time.Instant;

/**
 * DTO pushed to user-specific WebSocket queues — java-pro: Java 21 record, immutable.
 *
 * <p>JSON structure sent to {@code /user/{userId}/queue/notifications}:
 * <pre>
 * {
 *   "type":      "ORDER_STATUS_CHANGED",
 *   "title":     "Đơn hàng đã được xác nhận",
 *   "message":   "Đơn của bạn #TP2025001 đã chuyển sang CONFIRMED",
 *   "timestamp": "2025-04-09T10:30:00Z"
 * }
 * </pre>
 */
public record UserNotificationDto(
        UserNotificationType type,
        String title,
        String message,
        Instant timestamp
) {
    public enum UserNotificationType {
        ORDER_STATUS_CHANGED,
        RETURN_APPROVED,
        RETURN_REJECTED,
        VOUCHER_NEW
    }

    private static final java.util.Map<String, String> STATUS_LABELS = java.util.Map.of(
            "PENDING",    "Chờ xác nhận",
            "CONFIRMED",  "Đã xác nhận",
            "PROCESSING", "Đang xử lý",
            "SHIPPING",   "Đang giao hàng",
            "DELIVERED",  "Đã giao thành công",
            "CANCELLED",  "Đã hủy",
            "RETURNED",   "Đã hoàn hàng"
    );

    public static UserNotificationDto orderStatusChanged(String orderCode, String newStatus) {
        String label = STATUS_LABELS.getOrDefault(newStatus, newStatus);
        return new UserNotificationDto(
                UserNotificationType.ORDER_STATUS_CHANGED,
                "Cập nhật đơn hàng",
                String.format("Đơn #%s đã chuyển sang: %s", orderCode, label),
                Instant.now()
        );
    }

    public static UserNotificationDto returnApproved(String orderCode) {
        return new UserNotificationDto(
                UserNotificationType.RETURN_APPROVED,
                "Yêu cầu đổi/trả được duyệt",
                String.format("Yêu cầu đổi/trả cho đơn #%s đã được chấp thuận.", orderCode),
                Instant.now()
        );
    }

    public static UserNotificationDto returnRejected(String orderCode) {
        return new UserNotificationDto(
                UserNotificationType.RETURN_REJECTED,
                "Yêu cầu đổi/trả bị từ chối",
                String.format("Yêu cầu đổi/trả cho đơn #%s đã bị từ chối.", orderCode),
                Instant.now()
        );
    }
}
