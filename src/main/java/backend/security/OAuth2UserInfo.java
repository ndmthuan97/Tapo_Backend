package backend.security;

import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Utility record to extract standardized user info from an OAuth2User (Google).
 */
public record OAuth2UserInfo(
        String email,
        String fullName,
        String providerId,
        String avatarUrl
) {
    /**
     * Build from a Google OAuth2User's attributes.
     * Google provides: sub (providerId), email, name, picture
     */
    public static OAuth2UserInfo fromGoogle(OAuth2User oAuth2User) {
        return new OAuth2UserInfo(
                oAuth2User.getAttribute("email"),
                oAuth2User.getAttribute("name"),
                oAuth2User.getAttribute("sub"),
                oAuth2User.getAttribute("picture")
        );
    }
}
