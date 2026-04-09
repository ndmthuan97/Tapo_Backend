package backend.service.impl;

import backend.model.entity.Order;
import backend.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Async email service for order transactional emails.
 * All methods run on Spring's async executor — never blocks HTTP threads.
 * (java-pro: @Async + dedicated thread pool for I/O-bound mail operations)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String mailFrom;

    @Value("${app.mail.from-name:Tapo Store}")
    private String mailFromName;

    @Value("${app.base-url:http://localhost:5173}")
    private String appBaseUrl;

    // ── Public API ──────────────────────────────────────────────────────────────

    @Async
    @Override
    public void sendOrderConfirmation(Order order) {
        String subject = "🎉 Đặt hàng thành công - " + order.getOrderCode();
        String html = buildOrderConfirmationHtml(order);
        sendSafely(order.getUser().getEmail(), subject, html);
    }

    @Async
    @Override
    public void sendOrderDelivered(Order order) {
        String subject = "📦 Đơn hàng đã được giao - " + order.getOrderCode();
        String html = buildOrderDeliveredHtml(order);
        sendSafely(order.getUser().getEmail(), subject, html);
    }

    @Async
    @Override
    public void sendOrderCancelled(Order order) {
        String subject = "❌ Đơn hàng đã bị hủy - " + order.getOrderCode();
        String html = buildOrderCancelledHtml(order);
        sendSafely(order.getUser().getEmail(), subject, html);
    }

    @Async
    @Override
    public void sendContactReply(String toEmail, String customerName, String originalTopic, String replyContent) {
        String subject = "📨 Phản hồi từ Tapo Store: " + originalTopic;
        String html = buildContactReplyHtml(customerName, originalTopic, replyContent);
        sendSafely(toEmail, subject, html);
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    private void sendSafely(String to, String subject, String html) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(mailFrom, mailFromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(msg);
            log.info("[Email] Sent '{}' to {}", subject, to);
        } catch (Exception ex) {
            // Do NOT re-throw — email failure must never break the main transaction
            log.error("[Email] Failed to send '{}' to {}: {}", subject, to, ex.getMessage());
        }
    }

    private String formatVnd(java.math.BigDecimal amount) {
        return NumberFormat.getNumberInstance(Locale.of("vi", "VN"))
                .format(amount) + " ₫";
    }

    // ── HTML builders ────────────────────────────────────────────────────────────

    private String buildOrderConfirmationHtml(Order order) {
        String itemsHtml = order.getItems().stream()
                .map(i -> """
                    <tr>
                      <td style="padding:10px 0;border-bottom:1px solid #f3f4f6;">
                        <div style="display:flex;align-items:center;gap:12px;">
                          %s
                          <div>
                            <p style="margin:0;font-size:13px;font-weight:600;color:#374151;">%s</p>
                            <p style="margin:4px 0 0;font-size:11px;color:#9ca3af;">x%d</p>
                          </div>
                        </div>
                      </td>
                      <td style="padding:10px 0;border-bottom:1px solid #f3f4f6;text-align:right;font-size:13px;font-weight:700;color:#f97316;">%s</td>
                    </tr>
                    """.formatted(
                        i.getProductThumbnail() != null
                            ? "<img src=\"" + i.getProductThumbnail() + "\" width=\"44\" height=\"44\" style=\"border-radius:8px;object-fit:cover;\"/>"
                            : "<div style=\"width:44px;height:44px;background:#f3f4f6;border-radius:8px;\"></div>",
                        escapeHtml(i.getProductName()),
                        i.getQuantity(),
                        formatVnd(i.getTotalPrice())
                    ))
                .collect(Collectors.joining());

        String orderUrl = appBaseUrl + "/orders/" + order.getId();

        return """
            <!DOCTYPE html><html lang="vi"><head><meta charset="UTF-8"></head>
            <body style="margin:0;padding:0;background:#f5f5f5;font-family:Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="padding:40px 0;">
                <tr><td align="center">
                  <table width="600" cellpadding="0" cellspacing="0" style="background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);">
                    <tr><td style="background:linear-gradient(135deg,#f97316,#fb923c);padding:36px;text-align:center;">
                      <h1 style="color:#fff;margin:0;font-size:26px;font-weight:800;">Tapo Store</h1>
                      <p style="color:rgba(255,255,255,0.85);margin:8px 0 0;font-size:14px;">Đặt hàng thành công!</p>
                    </td></tr>
                    <tr><td style="padding:32px;">
                      <p style="color:#374151;font-size:15px;margin:0 0 8px;">Xin chào <strong>%s</strong>,</p>
                      <p style="color:#6b7280;font-size:13px;line-height:1.6;margin:0 0 24px;">
                        Cảm ơn bạn đã đặt hàng tại <strong>Tapo Store</strong>!<br>
                        Đơn hàng <strong>#%s</strong> của bạn đã được xác nhận và đang được xử lý.
                      </p>
                      <table width="100%%" cellpadding="0" cellspacing="0">%s</table>
                      <div style="border-top:2px solid #f97316;margin:16px 0;padding-top:16px;text-align:right;">
                        <p style="margin:0;font-size:16px;font-weight:800;color:#f97316;">Tổng: %s</p>
                      </div>
                      <div style="background:#f9fafb;border-radius:12px;padding:16px;margin:24px 0;font-size:13px;color:#374151;">
                        <p style="margin:0 0 6px;font-weight:700;">📍 Địa chỉ giao hàng</p>
                        <p style="margin:0;color:#6b7280;">%s — %s</p>
                        <p style="margin:4px 0 0;color:#6b7280;">%s, %s</p>
                      </div>
                      <div style="text-align:center;margin:24px 0 0;">
                        <a href="%s" style="display:inline-block;background:#f97316;color:#fff;text-decoration:none;padding:12px 32px;border-radius:10px;font-weight:700;font-size:14px;">
                          Xem chi tiết đơn hàng →
                        </a>
                      </div>
                    </td></tr>
                    <tr><td style="background:#f9fafb;padding:20px;text-align:center;border-top:1px solid #f3f4f6;">
                      <p style="color:#9ca3af;font-size:11px;margin:0;">© 2025 Tapo Store. Mọi quyền được bảo lưu.</p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body></html>
            """.formatted(
                escapeHtml(order.getUser().getFullName()),
                order.getOrderCode(),
                itemsHtml,
                formatVnd(order.getTotalAmount()),
                escapeHtml(order.getShippingRecipientName()),
                escapeHtml(order.getShippingPhone()),
                escapeHtml(order.getShippingAddress()),
                escapeHtml(order.getShippingDistrict() + ", " + order.getShippingCity()),
                orderUrl
            );
    }

    private String buildOrderDeliveredHtml(Order order) {
        String orderUrl = appBaseUrl + "/orders/" + order.getId();
        return """
            <!DOCTYPE html><html lang="vi"><head><meta charset="UTF-8"></head>
            <body style="margin:0;padding:0;background:#f5f5f5;font-family:Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="padding:40px 0;">
                <tr><td align="center">
                  <table width="600" cellpadding="0" cellspacing="0" style="background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);">
                    <tr><td style="background:linear-gradient(135deg,#10b981,#34d399);padding:36px;text-align:center;">
                      <h1 style="color:#fff;margin:0;font-size:26px;font-weight:800;">Tapo Store</h1>
                      <p style="color:rgba(255,255,255,0.85);margin:8px 0 0;font-size:14px;">📦 Đơn hàng đã được giao!</p>
                    </td></tr>
                    <tr><td style="padding:32px;">
                      <p style="color:#374151;font-size:15px;margin:0 0 16px;">Xin chào <strong>%s</strong>,</p>
                      <p style="color:#6b7280;font-size:13px;line-height:1.6;margin:0 0 24px;">
                        Đơn hàng <strong>#%s</strong> của bạn đã được giao thành công.<br>
                        Cảm ơn bạn đã mua sắm tại <strong>Tapo Store</strong>! 🎉
                      </p>
                      <p style="color:#6b7280;font-size:13px;">
                        Nếu bạn hài lòng với sản phẩm, hãy để lại đánh giá để giúp những khách hàng khác nhé!
                      </p>
                      <div style="text-align:center;margin:24px 0 0;">
                        <a href="%s" style="display:inline-block;background:#10b981;color:#fff;text-decoration:none;padding:12px 32px;border-radius:10px;font-weight:700;font-size:14px;">
                          Xem đơn hàng & Đánh giá →
                        </a>
                      </div>
                    </td></tr>
                    <tr><td style="background:#f9fafb;padding:20px;text-align:center;border-top:1px solid #f3f4f6;">
                      <p style="color:#9ca3af;font-size:11px;margin:0;">© 2025 Tapo Store.</p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body></html>
            """.formatted(
                escapeHtml(order.getUser().getFullName()),
                order.getOrderCode(),
                orderUrl
            );
    }

    private String buildOrderCancelledHtml(Order order) {
        String orderUrl = appBaseUrl + "/orders/" + order.getId();
        return """
            <!DOCTYPE html><html lang="vi"><head><meta charset="UTF-8"></head>
            <body style="margin:0;padding:0;background:#f5f5f5;font-family:Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="padding:40px 0;">
                <tr><td align="center">
                  <table width="600" cellpadding="0" cellspacing="0" style="background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);">
                    <tr><td style="background:linear-gradient(135deg,#ef4444,#f87171);padding:36px;text-align:center;">
                      <h1 style="color:#fff;margin:0;font-size:26px;font-weight:800;">Tapo Store</h1>
                      <p style="color:rgba(255,255,255,0.85);margin:8px 0 0;font-size:14px;">❌ Đơn hàng đã bị hủy</p>
                    </td></tr>
                    <tr><td style="padding:32px;">
                      <p style="color:#374151;font-size:15px;margin:0 0 16px;">Xin chào <strong>%s</strong>,</p>
                      <p style="color:#6b7280;font-size:13px;line-height:1.6;margin:0 0 24px;">
                        Đơn hàng <strong>#%s</strong> của bạn đã bị hủy.<br>
                        Nếu bạn cần hỗ trợ, xin liên hệ với chúng tôi.
                      </p>
                      <div style="text-align:center;margin:24px 0 0;">
                        <a href="%s" style="display:inline-block;background:#ef4444;color:#fff;text-decoration:none;padding:12px 32px;border-radius:10px;font-weight:700;font-size:14px;">
                          Xem chi tiết →
                        </a>
                      </div>
                    </td></tr>
                    <tr><td style="background:#f9fafb;padding:20px;text-align:center;border-top:1px solid #f3f4f6;">
                      <p style="color:#9ca3af;font-size:11px;margin:0;">© 2025 Tapo Store.</p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body></html>
            """.formatted(
                escapeHtml(order.getUser().getFullName()),
                order.getOrderCode(),
                orderUrl
            );
    }

    /** Escape HTML special chars — prevents XSS in email content */
    private String escapeHtml(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String buildContactReplyHtml(String customerName, String originalTopic, String replyContent) {
        return """
            <!DOCTYPE html><html lang="vi"><head><meta charset="UTF-8"></head>
            <body style="margin:0;padding:0;background:#f5f5f5;font-family:Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="padding:40px 0;">
                <tr><td align="center">
                  <table width="600" cellpadding="0" cellspacing="0" style="background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);">
                    <tr><td style="background:linear-gradient(135deg,#f97316,#fb923c);padding:36px;text-align:center;">
                      <h1 style="color:#fff;margin:0;font-size:26px;font-weight:800;">Tapo Store</h1>
                      <p style="color:rgba(255,255,255,0.85);margin:8px 0 0;font-size:14px;">Phản hồi từ đội ngũ hỗ trợ</p>
                    </td></tr>
                    <tr><td style="padding:32px;">
                      <p style="color:#374151;font-size:15px;margin:0 0 8px;">Xin chào <strong>%s</strong>,</p>
                      <p style="color:#6b7280;font-size:13px;line-height:1.6;margin:0 0 24px;">
                        Chúng tôi đã nhận được yêu cầu của bạn về chủ đề: <strong>%s</strong>.<br>
                        Dưới đây là phản hồi từ đội ngũ Tapo Store:
                      </p>
                      <div style="background:#fff7ed;border-left:4px solid #f97316;border-radius:0 12px 12px 0;padding:20px 24px;margin:0 0 24px;">
                        <p style="margin:0;font-size:14px;color:#374151;line-height:1.8;white-space:pre-line;">%s</p>
                      </div>
                      <p style="color:#6b7280;font-size:12px;margin:0;">Nếu bạn có thắc mắc thêm, đừng ngại liên hệ chúng tôi qua website nhé!</p>
                    </td></tr>
                    <tr><td style="background:#f9fafb;padding:20px;text-align:center;border-top:1px solid #f3f4f6;">
                      <p style="color:#9ca3af;font-size:11px;margin:0;">© 2025 Tapo Store. Mọi quyền được bảo lưu.</p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body></html>
            """.formatted(
            escapeHtml(customerName),
            escapeHtml(originalTopic),
            escapeHtml(replyContent)
        );
    }

    // ── Account lock/unlock emails ────────────────────────────────────────────────

    @Async
    @Override
    public void sendAccountLocked(String toEmail, String fullName) {
        String subject = "🔒 Tài khoản Tapo Store của bạn đã bị khóa";
        String html = buildAccountLockedHtml(fullName);
        sendSafely(toEmail, subject, html);
    }

    @Async
    @Override
    public void sendAccountUnlocked(String toEmail, String fullName) {
        String subject = "✅ Tài khoản Tapo Store của bạn đã được mở khóa";
        String html = buildAccountUnlockedHtml(fullName);
        sendSafely(toEmail, subject, html);
    }

    private String buildAccountLockedHtml(String fullName) {
        String contactUrl = appBaseUrl + "/contact";
        return """
            <!DOCTYPE html><html lang="vi"><head><meta charset="UTF-8"></head>
            <body style="margin:0;padding:0;background:#f5f5f5;font-family:Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="padding:40px 0;">
                <tr><td align="center">
                  <table width="600" cellpadding="0" cellspacing="0" style="background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);">
                    <tr><td style="background:linear-gradient(135deg,#ef4444,#f87171);padding:36px;text-align:center;">
                      <h1 style="color:#fff;margin:0;font-size:26px;font-weight:800;">Tapo Store</h1>
                      <p style="color:rgba(255,255,255,0.85);margin:8px 0 0;font-size:14px;">🔒 Thông báo tài khoản</p>
                    </td></tr>
                    <tr><td style="padding:32px;">
                      <p style="color:#374151;font-size:15px;margin:0 0 16px;">Xin chào <strong>%s</strong>,</p>
                      <p style="color:#6b7280;font-size:13px;line-height:1.8;margin:0 0 20px;">
                        Chúng tôi xin thông báo rằng tài khoản của bạn tại <strong>Tapo Store</strong>
                        đã bị <strong style="color:#ef4444;">tạm khóa</strong> bởi đội ngũ quản trị.
                      </p>
                      <div style="background:#fef2f2;border-left:4px solid #ef4444;border-radius:0 12px 12px 0;padding:16px 20px;margin:0 0 24px;">
                        <p style="margin:0;font-size:13px;color:#374151;line-height:1.6;">
                          Trong thời gian tài khoản bị khóa, bạn sẽ không thể đăng nhập
                          hoặc thực hiện các giao dịch trên hệ thống.
                        </p>
                      </div>
                      <p style="color:#6b7280;font-size:13px;margin:0 0 24px;">
                        Nếu bạn cho rằng đây là nhầm lẫn hoặc muốn khiếu nại, vui lòng liên hệ đội ngũ hỗ trợ ngay.
                      </p>
                      <div style="text-align:center;">
                        <a href="%s" style="display:inline-block;background:#ef4444;color:#fff;text-decoration:none;padding:12px 32px;border-radius:10px;font-weight:700;font-size:14px;">
                          Liên hệ hỗ trợ →
                        </a>
                      </div>
                    </td></tr>
                    <tr><td style="background:#f9fafb;padding:20px;text-align:center;border-top:1px solid #f3f4f6;">
                      <p style="color:#9ca3af;font-size:11px;margin:0;">© 2025 Tapo Store. Mọi quyền được bảo lưu.</p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body></html>
            """.formatted(escapeHtml(fullName), contactUrl);
    }

    private String buildAccountUnlockedHtml(String fullName) {
        String loginUrl = appBaseUrl + "/login";
        return """
            <!DOCTYPE html><html lang="vi"><head><meta charset="UTF-8"></head>
            <body style="margin:0;padding:0;background:#f5f5f5;font-family:Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="padding:40px 0;">
                <tr><td align="center">
                  <table width="600" cellpadding="0" cellspacing="0" style="background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);">
                    <tr><td style="background:linear-gradient(135deg,#10b981,#34d399);padding:36px;text-align:center;">
                      <h1 style="color:#fff;margin:0;font-size:26px;font-weight:800;">Tapo Store</h1>
                      <p style="color:rgba(255,255,255,0.85);margin:8px 0 0;font-size:14px;">✅ Tài khoản đã được kích hoạt</p>
                    </td></tr>
                    <tr><td style="padding:32px;">
                      <p style="color:#374151;font-size:15px;margin:0 0 16px;">Xin chào <strong>%s</strong>,</p>
                      <p style="color:#6b7280;font-size:13px;line-height:1.8;margin:0 0 20px;">
                        Chúng tôi vui mừng thông báo tài khoản của bạn tại <strong>Tapo Store</strong>
                        đã được <strong style="color:#10b981;">mở khóa</strong> và hoạt động trở lại bình thường.
                      </p>
                      <p style="color:#6b7280;font-size:13px;margin:0 0 24px;">
                        Bạn có thể đăng nhập và tiếp tục mua sắm ngay bây giờ. Cảm ơn sự kiên nhẫn của bạn!
                      </p>
                      <div style="text-align:center;">
                        <a href="%s" style="display:inline-block;background:#10b981;color:#fff;text-decoration:none;padding:12px 32px;border-radius:10px;font-weight:700;font-size:14px;">
                          Đăng nhập ngay →
                        </a>
                      </div>
                    </td></tr>
                    <tr><td style="background:#f9fafb;padding:20px;text-align:center;border-top:1px solid #f3f4f6;">
                      <p style="color:#9ca3af;font-size:11px;margin:0;">© 2025 Tapo Store. Mọi quyền được bảo lưu.</p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body></html>
            """.formatted(escapeHtml(fullName), loginUrl);
    }
}
