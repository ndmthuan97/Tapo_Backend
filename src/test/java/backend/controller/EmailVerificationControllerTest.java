package backend.controller;

import backend.dto.common.CustomCode;
import backend.exception.AppException;
import backend.exception.GlobalExceptionHandler;
import backend.service.EmailVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * EmailVerificationController Unit Tests.
 * Covers: AUTH-040, AUTH-041, AUTH-042, AUTH-043
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmailVerificationController — Unit Tests")
class EmailVerificationControllerTest {

    @Mock EmailVerificationService emailVerificationService;

    @InjectMocks EmailVerificationController emailVerificationController;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(emailVerificationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── GET /api/auth/verify-email ────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/auth/verify-email")
    class VerifyEmail {

        @Test
        @DisplayName("AUTH-040: token xác thực hợp lệ → 200, tài khoản được xác thực")
        void verifyEmail_validToken_200() throws Exception {
            willDoNothing().given(emailVerificationService).verifyEmail(anyString());

            mockMvc.perform(get("/api/auth/verify-email")
                            .param("token", "valid-uuid-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value(
                            "Tài khoản đã được xác thực thành công! Bạn có thể đăng nhập ngay."));
        }

        @Test
        @DisplayName("AUTH-041: token đã dùng → 400 VERIFICATION_TOKEN_INVALID")
        void verifyEmail_usedToken_400() throws Exception {
            willThrow(new AppException(CustomCode.VERIFICATION_TOKEN_INVALID))
                    .given(emailVerificationService).verifyEmail(anyString());

            mockMvc.perform(get("/api/auth/verify-email")
                            .param("token", "already-used-token"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("AUTH-042: token giả (UUID không tồn tại) → 400 VERIFICATION_TOKEN_INVALID")
        void verifyEmail_fakeToken_400() throws Exception {
            willThrow(new AppException(CustomCode.VERIFICATION_TOKEN_INVALID))
                    .given(emailVerificationService).verifyEmail(anyString());

            mockMvc.perform(get("/api/auth/verify-email")
                            .param("token", "00000000-0000-0000-0000-000000000000"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── POST /api/auth/resend-verification ────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/resend-verification")
    class ResendVerification {

        @Test
        @DisplayName("AUTH-043: gửi lại email xác thực cho email chưa verified → 200")
        void resendVerification_unverifiedEmail_200() throws Exception {
            willDoNothing().given(emailVerificationService).resendVerification(anyString());

            mockMvc.perform(post("/api/auth/resend-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"pending@tapo.vn\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value(
                            "Email xác thực đã được gửi lại. Vui lòng kiểm tra hộp thư của bạn."));
        }

        @Test
        @DisplayName("AUTH-043b: resend cho email đã verified → vẫn 200 (không lộ thông tin)")
        void resendVerification_alreadyVerified_200() throws Exception {
            // Service silently handles already-verified case
            willDoNothing().given(emailVerificationService).resendVerification(anyString());

            mockMvc.perform(post("/api/auth/resend-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"verified@tapo.vn\"}"))
                    .andExpect(status().isOk());
        }
    }
}
