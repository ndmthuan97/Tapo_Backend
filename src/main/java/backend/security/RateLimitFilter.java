package backend.security;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window rate limiter for auth endpoints.
 * java-pro: Lock-free ConcurrentHashMap + per-IP sliding timestamp window.
 *
 * <p>Limits: 10 requests per 60s for /api/auth/login and /api/auth/register.
 * Uses IP address as key. No Redis dependency — suitable for single-node.
 */
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int  MAX_REQUESTS  = 10;
    private static final long WINDOW_MS     = 60_000L; // 1 minute

    /** Set of path prefixes to rate-limit */
    private static final java.util.Set<String> RATE_LIMITED_PATHS = java.util.Set.of(
            "/api/auth/login",
            "/api/auth/register"
    );

    /** IP → deque of timestamps (long) within the window */
    private final ConcurrentHashMap<String, Deque<Long>> requestMap = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            @Nonnull HttpServletRequest  request,
            @Nonnull HttpServletResponse response,
            @Nonnull FilterChain         chain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();
        boolean shouldLimit = RATE_LIMITED_PATHS.stream().anyMatch(path::startsWith);

        if (!shouldLimit) {
            chain.doFilter(request, response);
            return;
        }

        String ip = resolveClientIp(request);
        long now = Instant.now().toEpochMilli();

        // Slide the window: remove timestamps older than WINDOW_MS
        Deque<Long> timestamps = requestMap.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            long cutoff = now - WINDOW_MS;
            while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= MAX_REQUESTS) {
                log.warn("[RateLimit] IP {} exceeded {} requests/min on {}", ip, MAX_REQUESTS, path);
                writeTooManyRequests(response);
                return;
            }

            timestamps.addLast(now);
        }

        chain.doFilter(request, response);
    }

    /** Extract real IP: respect X-Forwarded-For for reverse proxy setups */
    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"statusCode":429,"message":"Quá nhiều yêu cầu. Vui lòng thử lại sau 1 phút.","data":null,"errors":null}
                """.strip());
    }
}
