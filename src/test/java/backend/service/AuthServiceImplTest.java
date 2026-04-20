package backend.service;

import backend.constants.AppConstants;
import backend.dto.auth.AuthResponse;
import backend.dto.auth.LoginRequest;
import backend.dto.auth.RegisterRequest;
import backend.dto.auth.TokenRefreshRequest;
import backend.dto.common.CustomCode;
import backend.exception.AuthException;
import backend.model.entity.User;
import backend.model.enums.UserStatus;
import backend.repository.UserRepository;
import backend.security.JwtTokenProvider;
import backend.service.impl.AuthServiceImpl;
import backend.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService — Unit Tests")
class AuthServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthenticationManager authenticationManager;
    @Mock JwtTokenProvider tokenProvider;
    @Mock RefreshTokenRedisService refreshTokenRedisService;
    @Mock EmailVerificationService emailVerificationService;
    @Mock PasswordResetService passwordResetService;

    @InjectMocks
    AuthServiceImpl authService;

    @BeforeEach
    void clearMocks() {
        // MockitoExtension đã tự reset — explicit reset không cần
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // REGISTER
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("register()")
    class RegisterTests {

        @Test
        @DisplayName("register thành công → user PENDING_VERIFICATION, gửi verification email")
        void register_success() {
            RegisterRequest req = TestDataFactory.registerRequest();
            given(userRepository.existsByEmail(req.email())).willReturn(false);
            given(passwordEncoder.encode(req.password())).willReturn("hashed");

            User savedUser = TestDataFactory.pendingUser();
            savedUser.setEmail(req.email());
            given(userRepository.save(any(User.class))).willReturn(savedUser);

            AuthResponse response = authService.register(req);

            // register trả về null tokens — email chờ xác thực
            assertThat(response.accessToken()).isNull();
            assertThat(response.refreshToken()).isNull();
            verify(emailVerificationService).sendVerificationEmail(
                    anyString(), eq(req.email()), anyString());
        }

        @Test
        @DisplayName("register email đã tồn tại → throw EMAIL_ALREADY_EXISTS")
        void register_emailAlreadyExists() {
            RegisterRequest req = TestDataFactory.registerRequest();
            given(userRepository.existsByEmail(req.email())).willReturn(true);

            assertThatThrownBy(() -> authService.register(req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                            .isEqualTo(CustomCode.EMAIL_ALREADY_EXISTS));

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("register → user được gán role CUSTOMER và trạng thái PENDING_VERIFICATION")
        void register_setsCorrectRoleAndStatus() {
            RegisterRequest req = TestDataFactory.registerRequest();
            given(userRepository.existsByEmail(req.email())).willReturn(false);
            given(passwordEncoder.encode(any())).willReturn("hashed");

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            User savedUser = TestDataFactory.pendingUser();
            given(userRepository.save(captor.capture())).willReturn(savedUser);

            authService.register(req);

            User captured = captor.getValue();
            assertThat(captured.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
            assertThat(captured.getAvatarUrl()).isEqualTo(AppConstants.DEFAULT_AVATAR);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // LOGIN
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("login()")
    class LoginTests {

        @Test
        @DisplayName("login thành công → trả AuthResponse có access + refresh token")
        void login_success() {
            LoginRequest req = TestDataFactory.loginRequest();
            User user = TestDataFactory.activeUser();
            user.setEmail(req.email());

            given(userRepository.findByEmail(req.email())).willReturn(Optional.of(user));
            given(tokenProvider.generateAccessToken(user.getEmail())).willReturn("access-token");
            given(tokenProvider.generateRefreshToken(user.getEmail())).willReturn("refresh-token");
            given(tokenProvider.getJtiFromToken("refresh-token")).willReturn("jti-123");
            given(tokenProvider.getRefreshExpirationSeconds()).willReturn(604800L);

            AuthResponse response = authService.login(req);

            assertThat(response.accessToken()).isEqualTo("access-token");
            assertThat(response.refreshToken()).isEqualTo("refresh-token");
            assertThat(response.user().email()).isEqualTo(req.email());
        }

        @Test
        @DisplayName("login tài khoản bị LOCKED → throw ACCOUNT_LOCKED")
        void login_accountLocked() {
            LoginRequest req = TestDataFactory.loginRequest();
            User locked = TestDataFactory.lockedUser();
            locked.setEmail(req.email());

            given(userRepository.findByEmail(req.email())).willReturn(Optional.of(locked));

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                            .isEqualTo(CustomCode.ACCOUNT_LOCKED));
        }

        @Test
        @DisplayName("login email chưa xác thực → throw EMAIL_NOT_VERIFIED")
        void login_emailNotVerified() {
            LoginRequest req = TestDataFactory.loginRequest();
            User pending = TestDataFactory.pendingUser();
            pending.setEmail(req.email());

            given(userRepository.findByEmail(req.email())).willReturn(Optional.of(pending));

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                            .isEqualTo(CustomCode.EMAIL_NOT_VERIFIED));
        }

        @Test
        @DisplayName("login sai mật khẩu → AuthenticationManager throw → wrap thành CREDENTIALS_INVALID")
        void login_wrongCredentials() {
            LoginRequest req = TestDataFactory.loginRequest();
            doThrow(new BadCredentialsException("bad"))
                    .when(authenticationManager)
                    .authenticate(any(UsernamePasswordAuthenticationToken.class));

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                            .isEqualTo(CustomCode.CREDENTIALS_INVALID));
        }

        @Test
        @DisplayName("login thành công → cập nhật lastLoginAt")
        void login_updatesLastLoginAt() {
            LoginRequest req = TestDataFactory.loginRequest();
            User user = TestDataFactory.activeUser();
            user.setEmail(req.email());

            given(userRepository.findByEmail(req.email())).willReturn(Optional.of(user));
            given(tokenProvider.generateAccessToken(any())).willReturn("access");
            given(tokenProvider.generateRefreshToken(any())).willReturn("refresh");
            given(tokenProvider.getJtiFromToken(any())).willReturn("jti");
            given(tokenProvider.getRefreshExpirationSeconds()).willReturn(604800L);

            authService.login(req);

            assertThat(user.getLastLoginAt()).isNotNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // REFRESH TOKEN
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("refreshToken()")
    class RefreshTokenTests {

        @Test
        @DisplayName("token rotation thành công → revoke old jti, issue new pair")
        void refreshToken_valid() {
            String oldRefresh = "old-refresh";
            String oldAccess  = "old-access";
            User user = TestDataFactory.activeUser();

            given(tokenProvider.validateToken(oldRefresh)).willReturn(true);
            given(tokenProvider.getJtiFromToken(oldRefresh)).willReturn("old-jti");
            given(tokenProvider.getEmailFromToken(oldRefresh)).willReturn(user.getEmail());
            given(tokenProvider.getEmailIgnoreExpiry(oldAccess)).willReturn(user.getEmail());
            given(userRepository.findByEmail(user.getEmail())).willReturn(Optional.of(user));
            given(refreshTokenRedisService.validate("old-jti", user.getId().toString())).willReturn(true);
            given(tokenProvider.generateAccessToken(user.getEmail())).willReturn("new-access");
            given(tokenProvider.generateRefreshToken(user.getEmail())).willReturn("new-refresh");
            given(tokenProvider.getJtiFromToken("new-refresh")).willReturn("new-jti");
            given(tokenProvider.getRefreshExpirationSeconds()).willReturn(604800L);

            TokenRefreshRequest req = TestDataFactory.tokenRefreshRequest(oldAccess, oldRefresh);
            AuthResponse response = authService.refreshToken(req);

            assertThat(response.accessToken()).isEqualTo("new-access");
            verify(refreshTokenRedisService).revoke("old-jti", user.getId().toString());
        }

        @Test
        @DisplayName("refresh token hết hạn (validateToken fail) → throw EXPIRED_REFRESH_TOKEN")
        void refreshToken_expired() {
            given(tokenProvider.validateToken("bad-token")).willReturn(false);

            TokenRefreshRequest req = TestDataFactory.tokenRefreshRequest("access", "bad-token");
            assertThatThrownBy(() -> authService.refreshToken(req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                            .isEqualTo(CustomCode.EXPIRED_REFRESH_TOKEN));
        }

        @Test
        @DisplayName("email từ access và refresh token khác nhau → throw INVALID_REFRESH_TOKEN")
        void refreshToken_emailMismatch() {
            given(tokenProvider.validateToken("rt")).willReturn(true);
            given(tokenProvider.getJtiFromToken("rt")).willReturn("jti");
            given(tokenProvider.getEmailFromToken("rt")).willReturn("user1@tapo.vn");
            given(tokenProvider.getEmailIgnoreExpiry("at")).willReturn("user2@tapo.vn");

            TokenRefreshRequest req = TestDataFactory.tokenRefreshRequest("at", "rt");
            assertThatThrownBy(() -> authService.refreshToken(req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                            .isEqualTo(CustomCode.INVALID_REFRESH_TOKEN));
        }

        @Test
        @DisplayName("jti không tồn tại trong Redis → throw INVALID_REFRESH_TOKEN")
        void refreshToken_jtiNotInRedis() {
            User user = TestDataFactory.activeUser();
            given(tokenProvider.validateToken("rt")).willReturn(true);
            given(tokenProvider.getJtiFromToken("rt")).willReturn("jti");
            given(tokenProvider.getEmailFromToken("rt")).willReturn(user.getEmail());
            given(tokenProvider.getEmailIgnoreExpiry("at")).willReturn(user.getEmail());
            given(userRepository.findByEmail(user.getEmail())).willReturn(Optional.of(user));
            given(refreshTokenRedisService.validate("jti", user.getId().toString())).willReturn(false);

            TokenRefreshRequest req = TestDataFactory.tokenRefreshRequest("at", "rt");
            assertThatThrownBy(() -> authService.refreshToken(req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                            .isEqualTo(CustomCode.INVALID_REFRESH_TOKEN));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // LOGOUT
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("logout()")
    class LogoutTests {

        @Test
        @DisplayName("logout với refresh token hợp lệ → revoke jti")
        void logout_validToken() {
            User user = TestDataFactory.activeUser();
            given(tokenProvider.getJtiFromToken("valid-rt")).willReturn("jti-xyz");
            given(tokenProvider.getEmailFromToken("valid-rt")).willReturn(user.getEmail());
            given(userRepository.findByEmail(user.getEmail())).willReturn(Optional.of(user));

            authService.logout("valid-rt");

            verify(refreshTokenRedisService).revoke("jti-xyz", user.getId().toString());
        }

        @Test
        @DisplayName("logout với null token → không throw exception")
        void logout_nullToken() {
            authService.logout(null);
            verifyNoInteractions(refreshTokenRedisService);
        }

        @Test
        @DisplayName("logout với blank token → không throw exception")
        void logout_blankToken() {
            authService.logout("   ");
            verifyNoInteractions(refreshTokenRedisService);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // FORGOT / RESET PASSWORD
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("forgotPassword() & resetPassword()")
    class PasswordTests {

        @Test
        @DisplayName("forgotPassword email tồn tại → gửi reset email")
        void forgotPassword_existingEmail() {
            User user = TestDataFactory.activeUser();
            given(userRepository.findByEmail(user.getEmail())).willReturn(Optional.of(user));

            authService.forgotPassword(user.getEmail());

            verify(passwordResetService).sendResetEmail(
                    user.getId().toString(), user.getEmail(), user.getFullName());
        }

        @Test
        @DisplayName("forgotPassword email không tồn tại → im lặng, không gửi email")
        void forgotPassword_unknownEmail() {
            given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());

            authService.forgotPassword("unknown@tapo.vn");

            verifyNoInteractions(passwordResetService);
        }

        @Test
        @DisplayName("forgotPassword tài khoản LOCKED → không gửi email")
        void forgotPassword_lockedUser_noEmailSent() {
            User locked = TestDataFactory.lockedUser();
            given(userRepository.findByEmail(locked.getEmail())).willReturn(Optional.of(locked));

            authService.forgotPassword(locked.getEmail());

            verifyNoInteractions(passwordResetService);
        }

        @Test
        @DisplayName("resetPassword thành công → cập nhật passwordHash và invalidate token")
        void resetPassword_success() {
            User user = TestDataFactory.activeUser();
            String token = UUID.randomUUID().toString();

            given(passwordResetService.validateResetToken(token)).willReturn(user.getId().toString());
            given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
            given(passwordEncoder.encode("newPass123")).willReturn("newHash");

            authService.resetPassword(token, "newPass123");

            assertThat(user.getPasswordHash()).isEqualTo("newHash");
            verify(passwordResetService).invalidateToken(token, user.getId().toString());
        }

        @Test
        @DisplayName("resetPassword user PENDING → auto activate thành ACTIVE")
        void resetPassword_pendingUser_getsActivated() {
            User pending = TestDataFactory.pendingUser();
            String token = UUID.randomUUID().toString();

            given(passwordResetService.validateResetToken(token)).willReturn(pending.getId().toString());
            given(userRepository.findById(pending.getId())).willReturn(Optional.of(pending));
            given(passwordEncoder.encode(any())).willReturn("hash");

            authService.resetPassword(token, "newPass");

            assertThat(pending.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }
    }
}
