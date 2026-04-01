package backend.service;

public interface PasswordResetService {
    /**
     * Send a password reset email with a one-time token (TTL 1 hour).
     * Silently invalidates any previous pending token for this user.
     */
    void sendResetEmail(String userId, String email, String fullName);

    /**
     * Validate the reset token. Returns userId if valid.
     * Throws AuthException(RESET_TOKEN_INVALID) if not found or expired.
     */
    String validateResetToken(String token);

    /**
     * Invalidate the token after successful password reset (one-time use).
     */
    void invalidateToken(String token, String userId);
}
