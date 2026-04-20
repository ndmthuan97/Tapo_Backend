package backend.controller;

import backend.dto.auth.AuthResponse;
import backend.dto.auth.LoginRequest;
import backend.dto.auth.RegisterRequest;
import backend.dto.user.UserDto;
import backend.exception.AppException;
import backend.dto.common.CustomCode;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthController Unit Test — uses standaloneSetup to avoid Spring context overhead.
 * Zero Spring dependency.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController — Unit Tests")
class AuthControllerTest {

    @Mock AuthService authService;
    @Mock RateLimiterService rateLimiterService;

    @InjectMocks
    AuthController authController;

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
    }

    private AuthResponse mockAuthResponse() {
        UserDto user = new UserDto(
                UUID.randomUUID(), "Test User", "test@tapo.vn",
                null, null, UserRole.CUSTOMER, UserStatus.ACTIVE);
        return new AuthResponse("access-token", "refresh-token", user);
    }

    // ── Register ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/register")
    class RegisterEndpoint {

        @Test
        @DisplayName("register success → 200")
        void register_200() throws Exception {
            given(rateLimiterService.allowAuthAttempt(any())).willReturn(true);
            given(authService.register(any(RegisterRequest.class)))
                    .willReturn(mockAuthResponse());

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new RegisterRequest("new@tapo.vn", "password123", "Test User"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCode").value(200));
        }

        @Test
        @DisplayName("register exceeds rate limit → 429")
        void register_rateLimited_429() throws Exception {
            given(rateLimiterService.allowAuthAttempt(any())).willReturn(false);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new RegisterRequest("ok@tapo.vn", "pass123", "Name"))))
                    .andExpect(status().isTooManyRequests());
        }
    }

    // ── Login ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/login")
    class LoginEndpoint {

        @Test
        @DisplayName("login success → 200 with accessToken")
        void login_200() throws Exception {
            given(rateLimiterService.allowAuthAttempt(any())).willReturn(true);
            given(authService.login(any(LoginRequest.class))).willReturn(mockAuthResponse());

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new LoginRequest("test@tapo.vn", "password123"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").value("access-token"));
        }

        @Test
        @DisplayName("login exceeds rate limit → 429")
        void login_rateLimited_429() throws Exception {
            given(rateLimiterService.allowAuthAttempt(any())).willReturn(false);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new LoginRequest("test@tapo.vn", "pass"))))
                    .andExpect(status().isTooManyRequests());
        }
    }

    // ── Forgot Password ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/forgot-password")
    class ForgotPasswordEndpoint {

        @Test
        @DisplayName("forgot-password for unknown email → 200 (no email disclosure)")
        void forgotPassword_always200() throws Exception {
            mockMvc.perform(post("/api/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"anyone@example.com\"}"))
                    .andExpect(status().isOk());
        }
    }

    // ── Logout ────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/logout")
    class LogoutEndpoint {

        @Test
        @DisplayName("logout → 200")
        void logout_200() throws Exception {
            mockMvc.perform(post("/api/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }
    }
}
