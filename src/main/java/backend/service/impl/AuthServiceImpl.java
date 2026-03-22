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
import backend.service.RefreshTokenRedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenRedisService refreshTokenRedisService;

    // ── Register ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new AuthException(CustomCode.EMAIL_ALREADY_EXISTS);
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setPhoneNumber(request.phoneNumber());
        user.setRole(UserRole.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);
        user.setAvatarUrl(AppConstants.DEFAULT_AVATAR);

        User savedUser = userRepository.save(user);
        return generateAuthResponse(savedUser);
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );

            User user = userRepository.findByEmail(request.email())
                    .orElseThrow(() -> new AuthException(CustomCode.USER_NOT_FOUND));

            if (user.getStatus() == UserStatus.LOCKED) {
                throw new AuthException(CustomCode.ACCOUNT_LOCKED);
            }

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
