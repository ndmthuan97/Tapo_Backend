package backend.service;

/**
 * Manages refresh tokens stored in Redis.
 * Key schema:
 *   refresh:{jti}  → userId  (TTL = refresh token expiry)
 *   user:{userId}:refresh_tokens  → List of jtis (FIFO, capped at maxDevices)
 */
public interface RefreshTokenRedisService {

    /** Store a new token. Evicts oldest if limit exceeded. */
    void store(String userId, String jti, long ttlSeconds);

    /** Returns true iff jti exists in Redis AND belongs to userId. */
    boolean validate(String jti, String userId);

    /** Remove token from Redis (used on refresh rotation and logout). */
    void revoke(String jti, String userId);

    /** Remove ALL tokens for a user (full logout from all devices). */
    void revokeAll(String userId);
}
