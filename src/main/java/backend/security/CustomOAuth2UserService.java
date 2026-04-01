package backend.security;

import backend.constants.AppConstants;
import backend.model.entity.User;
import backend.model.enums.AuthProvider;
import backend.model.enums.UserRole;
import backend.model.enums.UserStatus;
import backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Custom OAuth2 user service — handles user lookup/auto-registration on Google sign-in.
 *
 * Behaviour:
 *   - New email → create user (CUSTOMER, ACTIVE, authProvider=GOOGLE)
 *   - Existing LOCAL user → link accounts by email, keep as-is (no password change)
 *   - Existing GOOGLE user → just return the existing user
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        OAuth2UserInfo info   = OAuth2UserInfo.fromGoogle(oAuth2User);

        if (info.email() == null || info.email().isBlank()) {
            throw new OAuth2AuthenticationException("Email not available from Google account");
        }

        userRepository.findByEmail(info.email().toLowerCase())
                .ifPresentOrElse(
                        existing -> updateProviderIfNeeded(existing, info),
                        () -> createNewUser(info)
                );

        return oAuth2User;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void updateProviderIfNeeded(User user, OAuth2UserInfo info) {
        boolean changed = false;
        // If user previously registered via LOCAL, update providerId for traceability
        if (user.getAuthProvider() == AuthProvider.LOCAL && info.providerId() != null) {
            user.setProviderId(info.providerId());
            changed = true;
        }
        // Sync avatar if empty
        if ((user.getAvatarUrl() == null || user.getAvatarUrl().equals(AppConstants.DEFAULT_AVATAR))
                && info.avatarUrl() != null) {
            user.setAvatarUrl(info.avatarUrl());
            changed = true;
        }
        if (changed) {
            userRepository.save(user);
            log.info("[OAuth2] Updated existing user {}", user.getEmail());
        }
    }

    private void createNewUser(OAuth2UserInfo info) {
        User user = new User();
        user.setEmail(info.email().toLowerCase());
        user.setFullName(info.fullName() != null ? info.fullName() : info.email());
        user.setAuthProvider(AuthProvider.GOOGLE);
        user.setProviderId(info.providerId());
        user.setRole(UserRole.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE); // Google accounts are pre-verified
        user.setAvatarUrl(info.avatarUrl() != null ? info.avatarUrl() : AppConstants.DEFAULT_AVATAR);
        // passwordHash intentionally null for OAuth users

        userRepository.save(user);
        log.info("[OAuth2] Created new Google user {}", user.getEmail());
    }
}
