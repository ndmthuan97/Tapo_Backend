package backend.service.impl;

import backend.dto.notification.AdminNotificationDto;
import backend.dto.notification.UserNotificationDto;
import backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * WebSocket notification service — broadcasts to both admin and user STOMP subscribers.
 *
 * <p>java-pro: @Async ensures notifications are fire-and-forget — caller never waits.
 * Virtual thread pool (configured in AsyncConfig) handles burst load efficiently.
 *
 * <p>Admin topic: {@code /topic/admin/notifications}
 * <p>User queue:  {@code /user/{userId}/queue/notifications} (via convertAndSendToUser)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final String ADMIN_TOPIC  = "/topic/admin/notifications";
    private static final String USER_QUEUE   = "/queue/notifications";

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

    @Async
    @Override
    public void notifyUserOrderStatusChanged(UUID userId, String orderCode, String newStatus) {
        sendToUser(userId.toString(), UserNotificationDto.orderStatusChanged(orderCode, newStatus));
    }

    @Async
    @Override
    public void notifyUserReturnDecision(UUID userId, String orderCode, boolean approved) {
        UserNotificationDto dto = approved
                ? UserNotificationDto.returnApproved(orderCode)
                : UserNotificationDto.returnRejected(orderCode);
        sendToUser(userId.toString(), dto);
    }

    @Override
    public void send(AdminNotificationDto notification) {
        try {
            messagingTemplate.convertAndSend(ADMIN_TOPIC, notification);
            log.debug("WS admin notification sent: type={}, message={}", notification.type(), notification.message());
        } catch (Exception ex) {
            // Notification failure must NOT propagate to business logic — log and continue
            log.warn("Failed to send WS admin notification: {}", ex.getMessage());
        }
    }

    /** Send to user-specific queue — uses Spring's convertAndSendToUser. */
    private void sendToUser(String userId, UserNotificationDto payload) {
        try {
            messagingTemplate.convertAndSendToUser(userId, USER_QUEUE, payload);
            log.debug("WS user notification sent: userId={}, type={}", userId, payload.type());
        } catch (Exception ex) {
            log.warn("Failed to send WS user notification to {}: {}", userId, ex.getMessage());
        }
    }
}
