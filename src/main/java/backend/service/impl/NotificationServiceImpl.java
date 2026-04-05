package backend.service.impl;

import backend.dto.notification.AdminNotificationDto;
import backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * WebSocket notification service — broadcasts to admin STOMP subscribers.
 *
 * <p>java-pro: @Async ensure notifications are fire-and-forget — caller never waits.
 * Virtual thread pool (configured in AsyncConfig) handles burst load efficiently.
 *
 * <p>Topic: {@code /topic/admin/notifications}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final String ADMIN_TOPIC = "/topic/admin/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    @Async
    @Override
    public void notifyNewOrder(String orderCode, String customerName, long totalAmount) {
        send(AdminNotificationDto.newOrder(orderCode, customerName, totalAmount));
    }

    @Async
    @Override
    public void notifyNewReturnRequest(String orderCode, String customerName) {
        send(AdminNotificationDto.newReturnRequest(orderCode, customerName));
    }

    @Async
    @Override
    public void notifyNewContactMessage(String senderName, String topic) {
        send(AdminNotificationDto.newContactMessage(senderName, topic));
    }

    @Override
    public void send(AdminNotificationDto notification) {
        try {
            messagingTemplate.convertAndSend(ADMIN_TOPIC, notification);
            log.debug("WS notification sent: type={}, message={}", notification.type(), notification.message());
        } catch (Exception ex) {
            // Notification failure must NOT propagate to business logic — log and continue
            log.warn("Failed to send WS notification: {}", ex.getMessage());
        }
    }
}
