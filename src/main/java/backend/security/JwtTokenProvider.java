package backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiration}")
    private long accessExpirationMs;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshExpirationMs;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // ── Token generation ────────────────────────────────────────────────────

    public String generateAccessToken(String email) {
        return buildToken(email, null, accessExpirationMs);
    }

    /** Generates a refresh token with a unique jti claim embedded. */
    public String generateRefreshToken(String email) {
        String jti = UUID.randomUUID().toString();
        return buildToken(email, jti, refreshExpirationMs);
    }

    private String buildToken(String email, String jti, long expiryMs) {
        Date now = new Date();
        JwtBuilder builder = Jwts.builder()
                .subject(email)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiryMs))
                .signWith(getSigningKey(), Jwts.SIG.HS256);
        if (jti != null) {
            builder.id(jti);
        }
        return builder.compact();
    }

    // ── Claims extraction ────────────────────────────────────────────────────

    public String getEmailFromToken(String token) {
        return getClaims(token).getSubject();
    }

    /** Extract jti from a refresh token. Returns null if absent. */
    public String getJtiFromToken(String token) {
        return getClaims(token).getId();
    }

    /**
     * Extract the subject (email) from a potentially-expired token.
     * Used to identify the user from an expired access token during refresh.
     */
    public String getEmailIgnoreExpiry(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        } catch (ExpiredJwtException ex) {
            // ExpiredJwtException still carries the claims
            return ex.getClaims().getSubject();
        }
    }

    // ── Validation ───────────────────────────────────────────────────────────

    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token");
        } catch (ExpiredJwtException ex) {
            log.error("Expired JWT token");
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token");
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty");
        }
        return false;
    }

    /** Returns expiry duration for a refresh token in seconds. */
    public long getRefreshExpirationSeconds() {
        return refreshExpirationMs / 1000;
    }

    // ── Private ──────────────────────────────────────────────────────────────

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
