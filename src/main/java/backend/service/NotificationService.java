package backend.service;

import backend.dto.notification.AdminNotificationDto;

/**
 * Notification service — pushes realtime events to admin WebSocket subscribers.
 *
 * <p>All methods are fire-and-forget: they broadcast to {@code /topic/admin/notifications}
 * and return immediately. Callers should NOT await or handle the result.
 *
 * <p>java-pro: interface segregation — keeps notification concerns separate from business logic.
 */
public interface NotificationService {

    /** New order placed by a customer. */
    void notifyNewOrder(String orderCode, String customerName, long totalAmount);

    /** Return/refund request submitted. */
    void notifyNewReturnRequest(String orderCode, String customerName);

    /** New contact message from the contact form. */
    void notifyNewContactMessage(String senderName, String topic);

    /**
     * Generic push — for future extensibility.
     * @param notification fully constructed DTO
     */
    void send(AdminNotificationDto notification);
}
