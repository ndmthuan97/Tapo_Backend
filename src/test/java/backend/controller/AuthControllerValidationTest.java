package backend.controller;

import backend.dto.auth.AuthResponse;
import backend.dto.auth.TokenRefreshRequest;
import backend.dto.user.UserDto;
import backend.dto.common.CustomCode;
import backend.exception.AppException;
import backend.exception.AuthException;
import backend.exception.GlobalExceptionHandler;
import backend.model.enums.UserRole;
import backend.model.enums.UserStatus;
import backend.service.AuthService;
import backend.service.RateLimiterService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthController — Validation & Token Tests.
 * Covers: AUTH-003, AUTH-006, AUTH-011, AUTH-014, AUTH-020, AUTH-022,
 *         AUTH-032, AUTH-033, AUTH-034
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController — Validation & Token Tests")
class AuthControllerValidationTest {

    @Mock AuthService authService;
    @Mock RateLimiterService rateLimiterService;

    @InjectMocks AuthController authController;

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private AuthResponse mockAuthResponse(UserRole role) {
        UserDto user = new UserDto(
                UUID.randomUUID(), "Test User", "test@tapo.vn",
                null, null, role, UserStatus.ACTIVE);
        return new AuthResponse("access-token", "refresh-token", user);
    }

    // ── Register Validation ───────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/register — validation")
    class RegisterValidation {

        @Test
        @DisplayName("AUTH-003: email sai format → 400 validation error")
        void register_invalidEmailFormat_400() throws Exception {
            String body = """
                    {"email":"not-an-email","password":"pass1234","fullName":"Test User"}
                    """;

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("AUTH-006: thiếu fullName → 400 validation error")
        void register_missingFullName_400() throws Exception {
            given(rateLimiterService.allowAuthAttempt(any())).willReturn(true);

            String body = """
                    {"email":"user@tapo.vn","password":"pass1234","fullName":""}
                    """;

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("AUTH-006b: fullName null → 400 validation error")
        void register_nullFullName_400() throws Exception {
            String body = """
                    {"email":"user@tapo.vn","password":"pass1234"}
                    """;

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── Login — Admin role ────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/login — roles")
    class LoginRoles {

        @Test
        @DisplayName("AUTH-011: đăng nhập ADMIN → 200, role=ADMIN")
        void login_adminRole_200() throws Exception {
            given(rateLimiterService.allowAuthAttempt(any())).willReturn(true);
            given(authService.login(any())).willReturn(mockAuthResponse(UserRole.ADMIN));

            String body = """
                    {"email":"admin@tapo.vn","password":"admin1234"}
                    """;

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.user.role").value("ADMIN"));
        }

        @Test
        @DisplayName("AUTH-014: email không tồn tại → 401 CREDENTIALS_INVALID")
        void login_emailNotFound_401() throws Exception {
            given(rateLimiterService.allowAuthAttempt(any())).willReturn(true);
            given(authService.login(any())).willThrow(new AuthException(CustomCode.CREDENTIALS_INVALID));

            String body = """
                    {"email":"nobody@tapo.vn","password":"pass1234"}
                    """;

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── Refresh Token ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/refresh-token")
    class RefreshToken {

        @Test
        @DisplayName("AUTH-020: refresh token hợp lệ → 200, accessToken mới")
        void refreshToken_valid_200() throws Exception {
            given(authService.refreshToken(any(TokenRefreshRequest.class)))
                    .willReturn(mockAuthResponse(UserRole.CUSTOMER));

            String body = """
                    {"accessToken":"old-access","refreshToken":"valid-refresh"}
                    """;

            mockMvc.perform(post("/api/auth/refresh-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").value("access-token"));
        }

        @Test
        @DisplayName("AUTH-022: refresh token giả mạo → 401 INVALID_REFRESH_TOKEN")
        void refreshToken_fakeToken_401() throws Exception {
            given(authService.refreshToken(any(TokenRefreshRequest.class)))
                    .willThrow(new AuthException(CustomCode.INVALID_REFRESH_TOKEN));

            String body = """
                    {"accessToken":"access","refreshToken":"fake-random-string"}
                    """;

            mockMvc.perform(post("/api/auth/refresh-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── Reset Password ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/reset-password")
    class ResetPassword {

        @Test
        @DisplayName("AUTH-032: token hợp lệ + newPassword → 200, password được đặt lại")
        void resetPassword_validToken_200() throws Exception {
            String body = """
                    {"token":"valid-uuid-token","newPassword":"NewPass@123"}
                    """;

            mockMvc.perform(post("/api/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value(
                            "Mật khẩu đã được đặt lại thành công. Bạn có thể đăng nhập ngay."));
        }

        @Test
        @DisplayName("AUTH-033: token đã dùng → 400 RESET_TOKEN_INVALID")
        void resetPassword_usedToken_400() throws Exception {
            willThrow(new AppException(CustomCode.RESET_TOKEN_INVALID))
                    .given(authService).resetPassword(any(), any());

            String body = """
                    {"token":"already-used-token","newPassword":"NewPass@123"}
                    """;

            mockMvc.perform(post("/api/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("AUTH-034: token trống → 400 validation error")
        void resetPassword_blankToken_400() throws Exception {
            String body = """
                    {"token":"","newPassword":"NewPass@123"}
                    """;

            mockMvc.perform(post("/api/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("AUTH-034b: newPassword ngắn hơn 6 ký tự → 400 validation error")
        void resetPassword_shortPassword_400() throws Exception {
            String body = """
                    {"token":"some-valid-token","newPassword":"abc"}
                    """;

            mockMvc.perform(post("/api/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }
}
