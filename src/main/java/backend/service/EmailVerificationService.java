package backend.service;

public interface EmailVerificationService {
    /** Send a verification email to the user. Generates a UUID token stored in Redis (TTL 24h). */
    void sendVerificationEmail(String userId, String email, String fullName);

    /** Verify a token from the email link. Activates user account. */
    void verifyEmail(String token);

    /** Resend verification email. Invalidates any existing token first. */
    void resendVerification(String email);
}
