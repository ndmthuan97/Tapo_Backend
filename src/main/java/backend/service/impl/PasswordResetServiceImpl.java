package backend.service.impl;

import backend.dto.common.CustomCode;
import backend.exception.AuthException;
import backend.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final String TOKEN_PREFIX = "pwd_reset:";        // token → userId
    private static final String USER_PREFIX  = "pwd_reset_user:";   // userId → token (for invalidation)
    private static final long   TOKEN_TTL_HOURS = 1;                 // 1 giờ

    private final StringRedisTemplate redis;
    private final JavaMailSender mailSender;

    @Value("${app.base-url:http://localhost:5173}")
    private String appBaseUrl;

    @Value("${spring.mail.username}")
    private String mailFrom;

    @Value("${app.mail.from-name:Tapo Store}")
    private String mailFromName;

    // ── Send reset email ──────────────────────────────────────────────────────

    @Async
    @Override
    public void sendResetEmail(String userId, String email, String fullName) {
        // Invalidate any previous pending token for this user
        invalidatePreviousToken(userId);

        String token = UUID.randomUUID().toString();

        // token → userId (for lookup during reset)
        redis.opsForValue().set(TOKEN_PREFIX + token, userId, TOKEN_TTL_HOURS, TimeUnit.HOURS);
        // userId → token (for invalidation on resend)
        redis.opsForValue().set(USER_PREFIX + userId, token, TOKEN_TTL_HOURS, TimeUnit.HOURS);

        String resetLink = appBaseUrl + "/reset-password?token=" + token;
        sendHtmlEmail(email, fullName, resetLink);
    }

    // ── Validate token ────────────────────────────────────────────────────────

    @Override
    public String validateResetToken(String token) {
        String userId = redis.opsForValue().get(TOKEN_PREFIX + token);
        if (userId == null) {
            throw new AuthException(CustomCode.RESET_TOKEN_INVALID);
        }
        return userId;
    }

    // ── Invalidate token (one-time use) ───────────────────────────────────────

    @Override
    public void invalidateToken(String token, String userId) {
        try {
            redis.delete(TOKEN_PREFIX + token);
            redis.delete(USER_PREFIX + userId);
        } catch (Exception ex) {
            log.warn("[PasswordReset] Could not invalidate token for userId {}: {}", userId, ex.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void invalidatePreviousToken(String userId) {
        try {
            String oldToken = redis.opsForValue().get(USER_PREFIX + userId);
            if (oldToken != null) {
                redis.delete(TOKEN_PREFIX + oldToken);
                redis.delete(USER_PREFIX + userId);
            }
        } catch (Exception ex) {
            log.warn("[PasswordReset] Could not invalidate previous token for userId {}: {}", userId, ex.getMessage());
        }
    }

    private void sendHtmlEmail(String to, String fullName, String resetLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailFrom, mailFromName);
            helper.setTo(to);
            helper.setSubject("🔐 Đặt lại mật khẩu Tapo Store");
            helper.setText(buildEmailHtml(fullName, resetLink), true);

            mailSender.send(message);
            log.info("[PasswordReset] Reset email sent to {}", to);
        } catch (Exception ex) {
            log.error("[PasswordReset] Failed to send reset email to {}: {}", to, ex.getMessage());
        }
    }

    private String buildEmailHtml(String fullName, String resetLink) {
        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
            <body style="margin:0;padding:0;background:#f5f5f5;font-family:Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f5f5f5;padding:40px 0;">
                <tr><td align="center">
                  <table width="600" cellpadding="0" cellspacing="0" style="background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);">
                    <!-- Header -->
                    <tr><td style="background:linear-gradient(135deg,#f97316,#fb923c);padding:40px;text-align:center;">
                      <h1 style="color:#fff;margin:0;font-size:28px;font-weight:800;">Tapo Store</h1>
                      <p style="color:rgba(255,255,255,0.85);margin:8px 0 0;font-size:14px;">Đặt lại mật khẩu của bạn</p>
                    </td></tr>
                    <!-- Body -->
                    <tr><td style="padding:40px;">
                      <p style="color:#374151;font-size:16px;margin:0 0 16px;">Xin chào <strong>%s</strong>,</p>
                      <p style="color:#6b7280;font-size:14px;line-height:1.6;margin:0 0 32px;">
                        Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản <strong>Tapo Store</strong> của bạn.<br>
                        Nhấn vào nút bên dưới để tạo mật khẩu mới. Link chỉ có hiệu lực trong <strong>1 giờ</strong>.
                      </p>
                      <div style="text-align:center;margin:0 0 32px;">
                        <a href="%s" style="display:inline-block;background:#f97316;color:#fff;text-decoration:none;padding:14px 40px;border-radius:12px;font-weight:700;font-size:15px;">
                          🔐 Đặt lại mật khẩu
                        </a>
                      </div>
                      <p style="color:#9ca3af;font-size:12px;line-height:1.5;margin:0;">
                        Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này.<br>
                        Tài khoản của bạn vẫn an toàn và mật khẩu cũ vẫn có hiệu lực.
                      </p>
                    </td></tr>
                    <!-- Footer -->
                    <tr><td style="background:#f9fafb;padding:20px;text-align:center;border-top:1px solid #f3f4f6;">
                      <p style="color:#9ca3af;font-size:11px;margin:0;">© 2025 Tapo Store. Mọi quyền được bảo lưu.</p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(fullName, resetLink);
    }
}
