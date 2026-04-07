package backend.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Centralized rate limiting service using Bucket4j + Upstash Redis.
 *
 * <p>Provides distributed, IP-based token-bucket rate limiting.
 * Can be injected into any controller or filter.
 *
 * <p>java-pro: stateless service with lazy bucket creation via ProxyManager.
 */
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final ProxyManager<String> bucketProxyManager;

    /**
     * Check whether a request is within the allowed rate.
     *
     * @param key      Unique bucket key (e.g., "rl:contact:192.168.1.1")
     * @param limit    Max requests allowed in the given window
     * @param window   Time window (e.g., Duration.ofMinutes(1))
     * @return {@code true} if request is allowed, {@code false} if rate limit exceeded
     */
    public boolean isAllowed(String key, long limit, Duration window) {
        Supplier<BucketConfiguration> configSupplier = () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(limit)
                        .refillGreedy(limit, window)
                        .build())
                .build();

        Bucket bucket = bucketProxyManager.builder().build(key, configSupplier);
        return bucket.tryConsume(1);
    }

    // ── Pre-configured rate limit presets ─────────────────────────────────────

    /**
     * Contact form: max 5 requests per minute per IP.
     */
    public boolean allowContactForm(String ip) {
        return isAllowed("rl:contact:" + ip, 5, Duration.ofMinutes(1));
    }

    /**
     * Auth attempts: max 10 requests per minute per IP.
     */
    public boolean allowAuthAttempt(String ip) {
        return isAllowed("rl:auth:" + ip, 10, Duration.ofMinutes(1));
    }

    /**
     * Order checkout: max 10 per minute per user.
     */
    public boolean allowCheckout(String userId) {
        return isAllowed("rl:checkout:" + userId, 10, Duration.ofMinutes(1));
    }
}
