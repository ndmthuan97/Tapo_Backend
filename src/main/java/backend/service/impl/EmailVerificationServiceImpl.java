package backend.service.impl;

import backend.dto.common.CustomCode;
import backend.exception.AuthException;
import backend.model.enums.UserStatus;
import backend.repository.UserRepository;
import backend.service.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private static final String TOKEN_PREFIX   = "email_verify:";
    private static final String USER_PREFIX    = "email_verify_user:"; // userId → token (for resend invalidation)
    private static final long   TOKEN_TTL_HOURS = 24;

    private final StringRedisTemplate redis;
    private final JavaMailSender mailSender;
    private final UserRepository userRepo;

    @Value("${app.base-url:http://localhost:5173}")
    private String appBaseUrl;

    @Value("${spring.mail.username}")
    private String mailFrom;

    @Value("${app.mail.from-name:Tapo Store}")
    private String mailFromName;

    // ── Send verification email ───────────────────────────────────────────────

    @Async
    @Override
    public void sendVerificationEmail(String userId, String email, String fullName) {
        // Invalidate any previous token for this user
        invalidatePreviousToken(userId);

        String token = UUID.randomUUID().toString();

        // Store token → userId (for verification lookup)
        redis.opsForValue().set(TOKEN_PREFIX + token, userId,
                TOKEN_TTL_HOURS, TimeUnit.HOURS);
        // Store userId → token (for resend invalidation)
        redis.opsForValue().set(USER_PREFIX + userId, token,
                TOKEN_TTL_HOURS, TimeUnit.HOURS);

        String verifyLink = appBaseUrl + "/verify-email?token=" + token;
        sendHtmlEmail(email, fullName, verifyLink);
    }

    // ── Verify token ──────────────────────────────────────────────────────────

    @Transactional
    @Override
    public void verifyEmail(String token) {
        String userId = redis.opsForValue().get(TOKEN_PREFIX + token);
        if (userId == null) {
            throw new AuthException(CustomCode.VERIFICATION_TOKEN_INVALID);
        }

        var user = userRepo.findById(UUID.fromString(userId))
                .orElseThrow(() -> new AuthException(CustomCode.USER_NOT_FOUND));

        user.setStatus(UserStatus.ACTIVE);
        userRepo.save(user);

        // Clean up Redis keys
        redis.delete(TOKEN_PREFIX + token);
        redis.delete(USER_PREFIX + userId);

        log.info("[EmailVerification] User {} verified successfully", user.getEmail());
    }

    // ── Resend verification email ─────────────────────────────────────────────

    @Override
    public void resendVerification(String email) {
        var user = userRepo.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new AuthException(CustomCode.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.ACTIVE) {
            // Already verified — silently return (don't leak info)
            return;
        }

        sendVerificationEmail(user.getId().toString(), user.getEmail(), user.getFullName());
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
            log.warn("[EmailVerification] Could not invalidate previous token: {}", ex.getMessage());
        }
    }

    private void sendHtmlEmail(String to, String fullName, String verifyLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailFrom, mailFromName);
            helper.setTo(to);
            helper.setSubject("✅ Xác nhận tài khoản Tapo Store");
            helper.setText(buildEmailHtml(fullName, verifyLink), true);

            mailSender.send(message);
            log.info("[EmailVerification] Verification email sent to {}", to);
        } catch (Exception ex) {
            // Catch all including MailSendException (runtime), MessagingException, etc.
            log.error("[EmailVerification] Failed to send to {}: {}", to, ex.getMessage());
        }
    }

    private String buildEmailHtml(String fullName, String verifyLink) {
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
                      <p style="color:rgba(255,255,255,0.85);margin:8px 0 0;font-size:14px;">Xác nhận địa chỉ email của bạn</p>
                    </td></tr>
                    <!-- Body -->
                    <tr><td style="padding:40px;">
                      <p style="color:#374151;font-size:16px;margin:0 0 16px;">Xin chào <strong>%s</strong>,</p>
                      <p style="color:#6b7280;font-size:14px;line-height:1.6;margin:0 0 32px;">
                        Cảm ơn bạn đã đăng ký tài khoản tại <strong>Tapo Store</strong>!<br>
                        Nhấn vào nút bên dưới để xác nhận địa chỉ email và kích hoạt tài khoản của bạn.
                      </p>
                      <div style="text-align:center;margin:0 0 32px;">
                        <a href="%s" style="display:inline-block;background:#f97316;color:#fff;text-decoration:none;padding:14px 40px;border-radius:12px;font-weight:700;font-size:15px;">
                          ✅ Xác nhận tài khoản
                        </a>
                      </div>
                      <p style="color:#9ca3af;font-size:12px;line-height:1.5;margin:0;">
                        Link xác nhận có hiệu lực trong <strong>24 giờ</strong>.<br>
                        Nếu bạn không đăng ký tài khoản, hãy bỏ qua email này.
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
            """.formatted(fullName, verifyLink);
    }
}
