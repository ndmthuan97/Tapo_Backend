package backend.flow;

import backend.dto.auth.AuthResponse;
import backend.dto.auth.LoginRequest;
import backend.dto.auth.RegisterRequest;
import backend.dto.common.CustomCode;
import backend.exception.AuthException;
import backend.model.entity.User;
import backend.model.enums.UserStatus;
import backend.repository.UserRepository;
import backend.security.JwtTokenProvider;
import backend.service.EmailVerificationService;
import backend.service.PasswordResetService;
import backend.service.RefreshTokenRedisService;
import backend.service.impl.AuthServiceImpl;
import backend.util.TestDataFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * Flow Test: Auth Full Lifecycle
 *
 * Flow: REGISTER → LOGIN (guards: PENDING / LOCKED / wrong creds) → LOGOUT → FORGOT_PASSWORD
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Auth Flow — Full Authentication Lifecycle")
class AuthFlowTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthenticationManager authenticationManager;
    @Mock JwtTokenProvider tokenProvider;
    @Mock RefreshTokenRedisService refreshTokenRedisService;
    @Mock EmailVerificationService emailVerificationService;
    @Mock PasswordResetService passwordResetService;

    @InjectMocks
    AuthServiceImpl authService;

    // ─── REGISTER ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("1. Register new user → status PENDING, sendVerificationEmail called with correct args")
    void register_createsPendingUser_sendsVerificationEmail() {
        RegisterRequest req = TestDataFactory.registerRequest("newuser@flow.test");

        given(userRepository.existsByEmail(req.email())).willReturn(false);
        given(passwordEncoder.encode(req.password())).willReturn("$hashed");

        User savedUser = new User();
        savedUser.setId(java.util.UUID.randomUUID());
        savedUser.setEmail(req.email());
        savedUser.setFullName(req.fullName());
        savedUser.setStatus(UserStatus.PENDING_VERIFICATION);
        given(userRepository.save(any(User.class))).willReturn(savedUser);

        authService.register(req);

        verify(userRepository).save(argThat(u ->
                u.getEmail().equals(req.email()) &&
                u.getStatus() == UserStatus.PENDING_VERIFICATION
        ));
        verify(emailVerificationService).sendVerificationEmail(anyString(), eq(req.email()), anyString());
    }

    @Test
    @DisplayName("1b. Register duplicate email → throw EMAIL_ALREADY_EXISTS, no email sent")
    void register_duplicateEmail_throws() {
        RegisterRequest req = TestDataFactory.registerRequest("existing@tapo.vn");
        given(userRepository.existsByEmail(req.email())).willReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                        .isEqualTo(CustomCode.EMAIL_ALREADY_EXISTS));

        verifyNoInteractions(emailVerificationService);
    }

    // ─── LOGIN GUARDS ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("2. Login while PENDING → throw EMAIL_NOT_VERIFIED")
    void login_pendingUser_throwsEmailNotVerified() {
        User pendingUser = TestDataFactory.pendingUser();
        LoginRequest req = new LoginRequest(pendingUser.getEmail(), "password123");

        given(authenticationManager.authenticate(any())).willReturn(
                new UsernamePasswordAuthenticationToken(pendingUser.getEmail(), null));
        given(userRepository.findByEmail(pendingUser.getEmail())).willReturn(Optional.of(pendingUser));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                        .isEqualTo(CustomCode.EMAIL_NOT_VERIFIED));

        verifyNoInteractions(tokenProvider);
        verifyNoInteractions(refreshTokenRedisService);
    }

    @Test
    @DisplayName("2b. Login while LOCKED → throw ACCOUNT_LOCKED")
    void login_lockedUser_throwsLocked() {
        User lockedUser = TestDataFactory.lockedUser();
        LoginRequest req = new LoginRequest(lockedUser.getEmail(), "password123");

        given(authenticationManager.authenticate(any())).willReturn(
                new UsernamePasswordAuthenticationToken(lockedUser.getEmail(), null));
        given(userRepository.findByEmail(lockedUser.getEmail())).willReturn(Optional.of(lockedUser));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                        .isEqualTo(CustomCode.ACCOUNT_LOCKED));
    }

    @Test
    @DisplayName("2c. Login with wrong password → throw CREDENTIALS_INVALID")
    void login_wrongPassword_throwsCredentialsInvalid() {
        LoginRequest req = new LoginRequest("test@tapo.vn", "wrongPass");

        given(authenticationManager.authenticate(any()))
                .willThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                        .isEqualTo(CustomCode.CREDENTIALS_INVALID));
    }

    // ─── LOGIN SUCCESS ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("3. Login active user → accessToken + refreshToken returned, Redis.store called")
    void login_activeUser_returnsTokenPair() {
        User activeUser = TestDataFactory.activeUser();
        LoginRequest req = new LoginRequest(activeUser.getEmail(), "password123");

        given(authenticationManager.authenticate(any())).willReturn(
                new UsernamePasswordAuthenticationToken(activeUser.getEmail(), null));
        given(userRepository.findByEmail(activeUser.getEmail())).willReturn(Optional.of(activeUser));
        given(userRepository.save(any())).willReturn(activeUser);
        given(tokenProvider.generateAccessToken(activeUser.getEmail())).willReturn("access-xyz");
        given(tokenProvider.generateRefreshToken(activeUser.getEmail())).willReturn("refresh-abc");
        given(tokenProvider.getJtiFromToken("refresh-abc")).willReturn("jti-123");
        given(tokenProvider.getRefreshExpirationSeconds()).willReturn(604800L);

        AuthResponse response = authService.login(req);

        assertThat(response.accessToken()).isEqualTo("access-xyz");
        assertThat(response.refreshToken()).isEqualTo("refresh-abc");
        assertThat(response.user().email()).isEqualTo(activeUser.getEmail());
        verify(refreshTokenRedisService).store(activeUser.getId().toString(), "jti-123", 604800L);
    }

    // ─── LOGOUT ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("4. Logout with valid refreshToken → jti revoked from Redis")
    void logout_validToken_revokesJti() {
        User activeUser = TestDataFactory.activeUser();
        String rawRefreshToken = "refresh-token-string";

        given(tokenProvider.getJtiFromToken(rawRefreshToken)).willReturn("jti-abc");
        given(tokenProvider.getEmailFromToken(rawRefreshToken)).willReturn(activeUser.getEmail());
        given(userRepository.findByEmail(activeUser.getEmail())).willReturn(Optional.of(activeUser));

        authService.logout(rawRefreshToken);

        verify(refreshTokenRedisService).revoke("jti-abc", activeUser.getId().toString());
    }

    @Test
    @DisplayName("4b. Logout with null token → silent no-op, no Redis interaction")
    void logout_nullToken_silentlyIgnored() {
        assertThatCode(() -> authService.logout(null))
                .doesNotThrowAnyException();

        verifyNoInteractions(tokenProvider, refreshTokenRedisService);
    }

    // ─── FORGOT PASSWORD ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("5. ForgotPassword for known email → sendResetEmail called with correct userId, email, name")
    void forgotPassword_knownEmail_sendsResetEmail() {
        User activeUser = TestDataFactory.activeUser();
        given(userRepository.findByEmail(activeUser.getEmail().toLowerCase()))
                .willReturn(Optional.of(activeUser));

        authService.forgotPassword(activeUser.getEmail());

        verify(passwordResetService).sendResetEmail(
                activeUser.getId().toString(),
                activeUser.getEmail(),
                activeUser.getFullName());
    }

    @Test
    @DisplayName("5b. ForgotPassword for unknown email → silent no-op, no reset email sent")
    void forgotPassword_unknownEmail_silentlyIgnored() {
        given(userRepository.findByEmail("ghost@test.vn")).willReturn(Optional.empty());

        assertThatCode(() -> authService.forgotPassword("ghost@test.vn"))
                .doesNotThrowAnyException();

        verifyNoInteractions(passwordResetService);
    }
}
