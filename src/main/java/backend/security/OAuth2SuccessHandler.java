package backend.security;

import backend.model.entity.User;
import backend.repository.UserRepository;
import backend.service.RefreshTokenRedisService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Handles successful Google OAuth2 login.
 *
 * Flow:
 *   1. Extract email from OAuth2User
 *   2. Load user from DB (created/updated by CustomOAuth2UserService)
 *   3. Generate JWT access + refresh token pair
 *   4. Store refresh token in Redis
 *   5. Redirect FE to /oauth/callback?accessToken=...&refreshToken=...
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenRedisService refreshTokenRedisService;
    private final UserRepository userRepository;

    @Value("${app.base-url:http://localhost:5173}")
    private String appBaseUrl;

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        if (email == null) {
            log.error("[OAuth2Success] Cannot extract email from OAuth2User");
            response.sendRedirect(appBaseUrl + "/login?error=oauth_email_missing");
            return;
        }

        User user = userRepository.findByEmail(email.toLowerCase()).orElseGet(() -> {
            // Should never happen — CustomOAuth2UserService creates user before this handler
            log.error("[OAuth2Success] User not found after OAuth2 flow: {}", email);
            return null;
        });

        if (user == null) {
            response.sendRedirect(appBaseUrl + "/login?error=oauth_user_not_found");
            return;
        }

        // Track last login time for analytics
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        // Generate JWT pair
        String accessToken  = tokenProvider.generateAccessToken(user.getEmail());
        String refreshToken = tokenProvider.generateRefreshToken(user.getEmail());

        // Store refresh token in Redis
        String jti = tokenProvider.getJtiFromToken(refreshToken);
        refreshTokenRedisService.store(
                user.getId().toString(),
                jti,
                tokenProvider.getRefreshExpirationSeconds()
        );

        log.info("[OAuth2Success] JWT issued for Google user: {}", user.getEmail());

        // Redirect to FE callback page with tokens in query params
        String redirectUrl = String.format(
                "%s/oauth/callback?accessToken=%s&refreshToken=%s",
                appBaseUrl,
                URLEncoder.encode(accessToken, StandardCharsets.UTF_8),
                URLEncoder.encode(refreshToken, StandardCharsets.UTF_8)
        );

        clearAuthenticationAttributes(request);
        response.sendRedirect(redirectUrl);
    }
}
