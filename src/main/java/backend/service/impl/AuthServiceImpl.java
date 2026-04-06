package backend.service.impl;

import backend.dto.auth.AuthResponse;
import backend.dto.auth.LoginRequest;
import backend.dto.auth.RegisterRequest;
import backend.dto.auth.TokenRefreshRequest;
import backend.dto.common.CustomCode;
import backend.dto.user.UserDto;
import backend.exception.AuthException;
import backend.model.entity.User;
import backend.model.enums.UserRole;
import backend.model.enums.UserStatus;
import backend.repository.UserRepository;
import backend.constants.AppConstants;
import backend.security.JwtTokenProvider;
import backend.service.AuthService;
import backend.service.EmailVerificationService;
import backend.service.PasswordResetService;
import backend.service.RefreshTokenRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenRedisService refreshTokenRedisService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;

    // ── Register ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        try {
            log.info("[REGISTER] Start for email: {}", request.email());

            if (userRepository.existsByEmail(request.email())) {
                throw new AuthException(CustomCode.EMAIL_ALREADY_EXISTS);
            }

            User user = new User();
            user.setEmail(request.email());
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            user.setFullName(request.fullName());
            user.setRole(UserRole.CUSTOMER);
            user.setStatus(UserStatus.PENDING_VERIFICATION);
            user.setAvatarUrl(AppConstants.DEFAULT_AVATAR);

            log.info("[REGISTER] Saving user...");
            User savedUser = userRepository.save(user);
            log.info("[REGISTER] User saved with id: {}", savedUser.getId());

            emailVerificationService.sendVerificationEmail(
                    savedUser.getId().toString(),
                    savedUser.getEmail(),
                    savedUser.getFullName()
            );
            log.info("[REGISTER] Verification email queued");

            return new AuthResponse(null, null, null);
        } catch (AuthException ex) {
            throw ex; // re-throw known errors
        } catch (Exception ex) {
            log.error("[REGISTER] UNEXPECTED ERROR: {} - {}", ex.getClass().getName(), ex.getMessage(), ex);
            throw ex;
        }
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );

            User user = userRepository.findByEmail(request.email())
                    .orElseThrow(() -> new AuthException(CustomCode.USER_NOT_FOUND));

            if (user.getStatus() == UserStatus.LOCKED) {
                throw new AuthException(CustomCode.ACCOUNT_LOCKED);
            }

            if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
                throw new AuthException(CustomCode.EMAIL_NOT_VERIFIED);
            }

            // Track last login time for analytics
            user.setLastLoginAt(Instant.now());
            userRepository.save(user);

            return generateAuthResponse(user);
        } catch (AuthenticationException ex) {
            throw new AuthException(CustomCode.CREDENTIALS_INVALID);
        }
    }

    // ── Refresh Token ─────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(TokenRefreshRequest request) {
        String incomingRefreshToken = request.refreshToken();

        // 1. Validate refresh token signature & expiry
        if (!tokenProvider.validateToken(incomingRefreshToken)) {
            throw new AuthException(CustomCode.EXPIRED_REFRESH_TOKEN);
        }

        // 2. Extract jti and email from refresh token
        String jti   = tokenProvider.getJtiFromToken(incomingRefreshToken);
        String email = tokenProvider.getEmailFromToken(incomingRefreshToken);

        // 3. Cross-check with access token (even if expired) for extra identity assurance
        String emailFromAccess = tokenProvider.getEmailIgnoreExpiry(request.accessToken());
        if (!email.equals(emailFromAccess)) {
            throw new AuthException(CustomCode.INVALID_REFRESH_TOKEN);
        }

        // 4. Verify jti exists in Redis and belongs to this user
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException(CustomCode.USER_NOT_FOUND));

        if (!refreshTokenRedisService.validate(jti, user.getId().toString())) {
            throw new AuthException(CustomCode.INVALID_REFRESH_TOKEN);
        }

        // 5. Token rotation — revoke old jti, issue new pair
        refreshTokenRedisService.revoke(jti, user.getId().toString());
        return generateAuthResponse(user);
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Override
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return;

        try {
            String jti   = tokenProvider.getJtiFromToken(refreshToken);
            String email = tokenProvider.getEmailFromToken(refreshToken);
            userRepository.findByEmail(email).ifPresent(user ->
                    refreshTokenRedisService.revoke(jti, user.getId().toString())
            );
        } catch (Exception ignored) {
            // Malformed / expired token during logout — silently ignore
        }
    }

    // ── Forgot Password ───────────────────────────────────────────────────────

    @Override
    public void forgotPassword(String email) {
        // Silently ignore unknown email — never reveal whether email exists
        userRepository.findByEmail(email.trim().toLowerCase()).ifPresent(user -> {
            if (user.getStatus() != UserStatus.LOCKED) {
                passwordResetService.sendResetEmail(
                        user.getId().toString(),
                        user.getEmail(),
                        user.getFullName()
                );
            }
        });
    }

    // ── Reset Password ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        String userId = passwordResetService.validateResetToken(token);

        User user = userRepository.findById(java.util.UUID.fromString(userId))
                .orElseThrow(() -> new AuthException(CustomCode.USER_NOT_FOUND));

        // Update password
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // Activate account if still pending (edge case)
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.setStatus(UserStatus.ACTIVE);
        }
        userRepository.save(user);

        // One-time use — invalidate token
        passwordResetService.invalidateToken(token, userId);

        log.info("[PasswordReset] Password reset successfully for user {}", user.getEmail());
    }

    // ── Private helper ────────────────────────────────────────────────────────

    private AuthResponse generateAuthResponse(User user) {
        String accessToken  = tokenProvider.generateAccessToken(user.getEmail());
        String refreshToken = tokenProvider.generateRefreshToken(user.getEmail());

        // Extract jti from the newly created refresh token and store in Redis
        String jti = tokenProvider.getJtiFromToken(refreshToken);
        refreshTokenRedisService.store(
                user.getId().toString(),
                jti,
                tokenProvider.getRefreshExpirationSeconds()
        );

        UserDto userDto = new UserDto(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getStatus()
        );

        return new AuthResponse(accessToken, refreshToken, userDto);
    }
}
