package backend.service.impl;

import backend.service.RefreshTokenRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenRedisServiceImpl implements RefreshTokenRedisService {

    private static final int MAX_DEVICES = 3;
    private static final String TOKEN_KEY_PREFIX = "refresh:";
    private static final String USER_LIST_PREFIX  = "user:refresh:";

    private final StringRedisTemplate redis;

    // ── public API ───────────────────────────────────────────────────────────

    @Override
    public void store(String userId, String jti, long ttlSeconds) {
        try {
            // 1. Map jti → userId with TTL
            redis.opsForValue().set(TOKEN_KEY_PREFIX + jti, userId, ttlSeconds, TimeUnit.SECONDS);

            // 2. Append jti to the user's device list (rightmost = newest)
            String listKey = USER_LIST_PREFIX + userId;
            redis.opsForList().rightPush(listKey, jti);
            redis.expire(listKey, ttlSeconds + 60, TimeUnit.SECONDS);

            // 3. Enforce device limit
            enforceDeviceLimit(userId, listKey);
        } catch (Exception ex) {
            log.warn("[Redis] Failed to store refresh token for user={}: {}", userId, ex.getMessage());
            // Non-fatal: user still gets tokens, Redis just won't enforce device limit
        }
    }

    @Override
    public boolean validate(String jti, String userId) {
        try {
            String storedUserId = redis.opsForValue().get(TOKEN_KEY_PREFIX + jti);
            return userId.equals(storedUserId);
        } catch (Exception ex) {
            log.warn("[Redis] validate() failed for jti={}: {}", jti, ex.getMessage());
            // Redis unavailable → allow token through (fail-open)
            // Better UX than kicking out all users when Redis hiccups
            return true;
        }
    }

    @Override
    public void revoke(String jti, String userId) {
        try {
            redis.delete(TOKEN_KEY_PREFIX + jti);
            redis.opsForList().remove(USER_LIST_PREFIX + userId, 1, jti);
        } catch (Exception ex) {
            log.warn("[Redis] revoke() failed for jti={}: {}", jti, ex.getMessage());
        }
    }

    @Override
    public void revokeAll(String userId) {
        String listKey = USER_LIST_PREFIX + userId;
        List<String> jtis = redis.opsForList().range(listKey, 0, -1);
        if (jtis != null) {
            jtis.forEach(jti -> redis.delete(TOKEN_KEY_PREFIX + jti));
        }
        redis.delete(listKey);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private void enforceDeviceLimit(String userId, String listKey) {
        Long size = redis.opsForList().size(listKey);
        if (size == null) return;

        while (size > MAX_DEVICES) {
            // Pop oldest jti from the left end and revoke it
            String oldJti = redis.opsForList().leftPop(listKey);
            if (oldJti != null) {
                redis.delete(TOKEN_KEY_PREFIX + oldJti);
                log.info("Evicted oldest refresh token for user {}: jti={}", userId, oldJti);
            }
            size--;
        }
    }
}
