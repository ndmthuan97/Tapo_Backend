package backend.service;

import backend.dto.notification.AdminNotificationDto;
import backend.dto.notification.UserNotificationDto;
import java.util.UUID;

/**
 * Notification service — pushes realtime events to WebSocket subscribers.
 *
 * <p>All methods are fire-and-forget: they broadcast to the appropriate topic/queue
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

    /** Order status updated by admin — push to customer's personal queue. */
    void notifyUserOrderStatusChanged(UUID userId, String orderCode, String newStatus);

    /** Return request decision (approved/rejected) pushed to customer. */
    void notifyUserReturnDecision(UUID userId, String orderCode, boolean approved);

    /**
     * Generic push to admin topic — for future extensibility.
     * @param notification fully constructed DTO
     */
    void send(AdminNotificationDto notification);
}
