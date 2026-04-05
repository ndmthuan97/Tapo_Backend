package backend.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * CorrelationIdFilter — java-pro: distributed tracing + structured logging.
 *
 * <p>Strategy:
 * <ol>
 *   <li>Đọc {@code X-Request-ID} từ Nginx (forward compatible khi self-host)</li>
 *   <li>Fallback: sinh UUID 8-ký tự nếu không có header</li>
 *   <li>Inject vào SLF4J MDC key {@code traceId} → xuất hiện tự động trong mọi log line</li>
 *   <li>Echo lại {@code X-Trace-Id} trong response header để client debug</li>
 *   <li><b>Bắt buộc</b> cleanup MDC trong {@code finally} — tránh thread pool contamination</li>
 * </ol>
 *
 * <p>Ordering: Order(1) — chạy trước mọi filter khác để mọi log đều có traceId.
 */
@Slf4j
@Component
@Order(1)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String NGINX_REQUEST_ID_HEADER = "X-Request-ID";
    private static final String RESPONSE_TRACE_HEADER   = "X-Trace-Id";
    private static final String MDC_KEY                 = "traceId";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String traceId = resolveTraceId(request);
        MDC.put(MDC_KEY, traceId);
        response.addHeader(RESPONSE_TRACE_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // java-pro: MUST cleanup MDC — virtual threads reuse platform threads via carrier
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * Resolve trace ID: prefer Nginx X-Request-ID (production), fallback to generated UUID.
     * UUID truncated to 8 hex chars — sufficient uniqueness, minimal log overhead.
     */
    private String resolveTraceId(HttpServletRequest request) {
        String fromNginx = request.getHeader(NGINX_REQUEST_ID_HEADER);
        if (StringUtils.hasText(fromNginx)) {
            // Truncate long nginx $request_id to first 16 chars
            return fromNginx.length() > 16 ? fromNginx.substring(0, 16) : fromNginx;
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
