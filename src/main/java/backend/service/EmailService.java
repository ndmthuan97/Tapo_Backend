package backend.service;

import backend.model.entity.Order;

/**
 * Abstraction for all transactional email sending.
 * Implementations are async (@Async) to avoid blocking the main thread.
 */
public interface EmailService {

    /** Gửi email xác nhận đơn hàng vừa được tạo */
    void sendOrderConfirmation(Order order);

    /** Gửi email thông báo đơn hàng đã được giao */
    void sendOrderDelivered(Order order);

    /** Gửi email thông báo đơn hàng bị hủy */
    void sendOrderCancelled(Order order);

    /**
     * Gửi email phản hồi tới khách hàng đã gửi contact form.
     *
     * @param toEmail     Email của khách cần phản hồi
     * @param customerName Tên khách
     * @param originalTopic Chủ đề gốc của khách
     * @param replyContent  Nội dung admin phản hồi
     */
    void sendContactReply(String toEmail, String customerName, String originalTopic, String replyContent);

    /** Gửi email thông báo tài khoản đã bị khóa bởi admin */
    void sendAccountLocked(String toEmail, String fullName);

    /** Gửi email thông báo tài khoản đã được mở khóa bởi admin */
    void sendAccountUnlocked(String toEmail, String fullName);
}
