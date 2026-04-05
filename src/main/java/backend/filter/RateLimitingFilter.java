package backend.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

/**
 * RateLimitingFilter — java-pro: Bucket4j token-bucket algorithm với Redis backing store.
 *
 * <p>Rules (per IP):
 * <ul>
 *   <li>{@code POST /api/auth/login}    → 10 req/phút</li>
 *   <li>{@code POST /api/auth/register} → 5 req/phút</li>
 *   <li>{@code POST /api/contact}       → 3 req/phút</li>
 * </ul>
 *
 * <p>Response khi vượt limit: {@code 429 Too Many Requests} + {@code Retry-After: 60} header
 * + JSON body theo ApiResponse standard contract.
 *
 * <p>Ordering: Order(2) — sau CorrelationIdFilter (traceId đã có) trước Spring Security.
 */
@Slf4j
@Component
@Order(2)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final ProxyManager<String> bucketProxyManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RateLimitingFilter(ProxyManager<String> bucketProxyManager) {
        this.bucketProxyManager = bucketProxyManager;
    }

    // ── Rate limit configs (các rule cố định — java-pro: static map O(1) lookup) ──────────────

    private record RuleConfig(int tokens, Duration refill) {}

    private static final Map<String, RuleConfig> RULES = Map.of(
        "POST:/api/auth/login",    new RuleConfig(10, Duration.ofMinutes(1)),
        "POST:/api/auth/register", new RuleConfig(5,  Duration.ofMinutes(1)),
        "POST:/api/contact",       new RuleConfig(3,  Duration.ofMinutes(1))
    );

    // ── Filter logic ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void doFilterInternal(
            @Nonnull HttpServletRequest request,
            @Nonnull HttpServletResponse response,
            @Nonnull FilterChain filterChain
    ) throws ServletException, IOException {

        String ruleKey = request.getMethod() + ":" + request.getRequestURI();
        RuleConfig rule = RULES.get(ruleKey);

        // Không có rule → pass through (không rate limit)
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp  = resolveClientIp(request);
        String bucketKey = "rl:" + ruleKey + ":" + clientIp;

        Supplier<BucketConfiguration> configSupplier = () -> BucketConfiguration.builder()
            .addLimit(Bandwidth.builder()
                .capacity(rule.tokens())
                .refillGreedy(rule.tokens(), rule.refill())
                .build())
            .build();

        var bucket = bucketProxyManager.builder().build(bucketKey, configSupplier);

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("[RateLimit] IP={} exceeded limit for {}", clientIp, ruleKey);
            sendTooManyRequests(response, rule.refill().getSeconds());
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────────────────────

    /**
     * Resolve real client IP — hỗ trợ Nginx X-Forwarded-For khi self-host.
     * java-pro: always prefer X-Forwarded-For trong production reverse proxy.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();  // Lấy IP đầu tiên (client thực)
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    /** Trả về 429 theo standard API contract: { statusCode, message, data, errors }. */
    private void sendTooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));

        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("statusCode", 429);
        body.put("message",    "Quá nhiều yêu cầu. Vui lòng thử lại sau " + retryAfterSeconds + " giây.");
        body.put("data",       null);
        body.put("errors",     null);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
