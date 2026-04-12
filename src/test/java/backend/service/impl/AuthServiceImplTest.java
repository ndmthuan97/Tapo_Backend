package backend.service.impl;

import backend.dto.auth.LoginRequest;
import backend.dto.auth.RegisterRequest;
import backend.dto.auth.TokenRefreshRequest;
import backend.dto.common.CustomCode;
import backend.exception.AuthException;
import backend.model.entity.User;
import backend.model.enums.UserRole;
import backend.model.enums.UserStatus;
import backend.repository.UserRepository;
import backend.security.JwtTokenProvider;
import backend.service.EmailVerificationService;
import backend.service.PasswordResetService;
import backend.service.RefreshTokenRedisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for AuthServiceImpl — testing-patterns skill applied:
 * - Factory functions for stub data (stubUser, stubActiveUser)
 * - Behaviour-based assertions (not mock verification)
 * - Nested describe blocks for clarity
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl — Unit Tests")
class AuthServiceImplTest {

    @Mock UserRepository              userRepository;
    @Mock PasswordEncoder             passwordEncoder;
    @Mock AuthenticationManager       authenticationManager;
    @Mock JwtTokenProvider            tokenProvider;
    @Mock RefreshTokenRedisService    refreshTokenRedisService;
    @Mock EmailVerificationService    emailVerificationService;
    @Mock PasswordResetService        passwordResetService;

    @InjectMocks AuthServiceImpl authService;

    // ── register ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("duplicate email → throws EMAIL_ALREADY_EXISTS")
        void register_duplicateEmail_throwsAuthException() {
            given(userRepository.existsByEmail("dup@test.com")).willReturn(true);

            assertThatThrownBy(() -> authService.register(stubRegisterRequest("dup@test.com")))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining(CustomCode.EMAIL_ALREADY_EXISTS.getDefaultMessage());

            then(userRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("new email → saves user with PENDING_VERIFICATION status")
        void register_newEmail_savesPendingUser() {
            var request = stubRegisterRequest("new@test.com");
            given(userRepository.existsByEmail("new@test.com")).willReturn(false);
            given(passwordEncoder.encode(any())).willReturn("hashed-pw");
            // java-pro: return a User with ID so emailVerificationService.sendVerificationEmail doesn't NPE
            User saved = stubUser("new@test.com");
            given(userRepository.save(any())).willReturn(saved);
            willDoNothing().given(emailVerificationService)
                    .sendVerificationEmail(anyString(), anyString(), anyString());

            authService.register(request);

            // testing-patterns: test behaviour (user saved with correct state)
            then(userRepository).should().save(argThat(u ->
                    u.getEmail().equals("new@test.com") &&
                    u.getStatus() == UserStatus.PENDING_VERIFICATION &&
                    u.getRole() == UserRole.CUSTOMER
            ));
        }

        @Test
        @DisplayName("new email → verification email sent")
        void register_newEmail_sendsVerificationEmail() {
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(passwordEncoder.encode(any())).willReturn("hashed-pw");
            User saved = stubUser("user@test.com");
            given(userRepository.save(any())).willReturn(saved);
            willDoNothing().given(emailVerificationService)
                    .sendVerificationEmail(anyString(), anyString(), anyString());

            authService.register(stubRegisterRequest("user@test.com"));

            then(emailVerificationService).should().sendVerificationEmail(anyString(), anyString(), anyString());
        }
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("bad credentials → throws CREDENTIALS_INVALID")
        void login_badCredentials_throwsCredentialsInvalid() {
            given(authenticationManager.authenticate(any()))
                    .willThrow(new BadCredentialsException("bad"));

            assertThatThrownBy(() -> authService.login(stubLoginRequest()))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining(CustomCode.CREDENTIALS_INVALID.getDefaultMessage());
        }

        @Test
        @DisplayName("LOCKED user → throws ACCOUNT_LOCKED")
        void login_lockedAccount_throwsAccountLocked() {
            User locked = stubUser("locked@test.com");
            locked.setStatus(UserStatus.LOCKED);
            given(authenticationManager.authenticate(any())).willReturn(null);
            given(userRepository.findByEmail("locked@test.com")).willReturn(Optional.of(locked));

            assertThatThrownBy(() -> authService.login(new LoginRequest("locked@test.com", "pass")))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining(CustomCode.ACCOUNT_LOCKED.getDefaultMessage());
        }

        @Test
        @DisplayName("PENDING_VERIFICATION user → throws EMAIL_NOT_VERIFIED")
        void login_unverifiedEmail_throwsEmailNotVerified() {
            User unverified = stubUser("unverified@test.com");
            unverified.setStatus(UserStatus.PENDING_VERIFICATION);
            given(authenticationManager.authenticate(any())).willReturn(null);
            given(userRepository.findByEmail("unverified@test.com")).willReturn(Optional.of(unverified));

            assertThatThrownBy(() -> authService.login(new LoginRequest("unverified@test.com", "pass")))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining(CustomCode.EMAIL_NOT_VERIFIED.getDefaultMessage());
        }

        @Test
        @DisplayName("valid ACTIVE user → returns AuthResponse with tokens")
        void login_validActiveUser_returnsAuthResponse() {
            User user = stubActiveUser("user@test.com");
            given(authenticationManager.authenticate(any())).willReturn(null);
            given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(user));
            given(userRepository.save(any())).willReturn(user);
            given(tokenProvider.generateAccessToken(anyString())).willReturn("access-token");
            given(tokenProvider.generateRefreshToken(anyString())).willReturn("refresh-token");
            given(tokenProvider.getJtiFromToken("refresh-token")).willReturn("jti-123");
            given(tokenProvider.getRefreshExpirationSeconds()).willReturn(604800L);
            willDoNothing().given(refreshTokenRedisService).store(anyString(), anyString(), anyLong());

            var response = authService.login(new LoginRequest("user@test.com", "pass"));

            assertThat(response.accessToken()).isEqualTo("access-token");
            assertThat(response.refreshToken()).isEqualTo("refresh-token");
            assertThat(response.user()).isNotNull();
        }
    }

    // ── forgotPassword ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("forgotPassword()")
    class ForgotPassword {

        @Test
        @DisplayName("unknown email → silently ignores (no exception thrown)")
        void forgotPassword_unknownEmail_noop() {
            given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());

            assertThatCode(() -> authService.forgotPassword("unknown@test.com"))
                    .doesNotThrowAnyException();

            then(passwordResetService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("locked account → email not sent (security)")
        void forgotPassword_lockedAccount_doesNotSendEmail() {
            User locked = stubUser("locked@test.com");
            locked.setStatus(UserStatus.LOCKED);
            given(userRepository.findByEmail("locked@test.com")).willReturn(Optional.of(locked));

            authService.forgotPassword("locked@test.com");

            then(passwordResetService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("active account → sends reset email")
        void forgotPassword_activeAccount_sendsEmail() {
            User user = stubActiveUser("user@test.com");
            given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(user));
            willDoNothing().given(passwordResetService)
                    .sendResetEmail(anyString(), anyString(), anyString());

            authService.forgotPassword("user@test.com");

            then(passwordResetService).should().sendResetEmail(anyString(), anyString(), anyString());
        }
    }

    // ── Test fixtures ─────────────────────────────────────────────────────────

    private RegisterRequest stubRegisterRequest(String email) {
        return new RegisterRequest(email, "SecurePass@123", "Nguyen Van Test");
    }

    private LoginRequest stubLoginRequest() {
        return new LoginRequest("user@test.com", "pass");
    }

    /** Factory: user with PENDING_VERIFICATION status */
    private User stubUser(String email) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setFullName("Test User");
        u.setPasswordHash("hashed-pw");
        u.setRole(UserRole.CUSTOMER);
        u.setStatus(UserStatus.PENDING_VERIFICATION);
        return u;
    }

    /** Factory: fully activated user */
    private User stubActiveUser(String email) {
        User u = stubUser(email);
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }
}
