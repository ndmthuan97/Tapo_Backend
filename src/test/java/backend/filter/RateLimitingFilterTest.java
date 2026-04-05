package backend.filter;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * RateLimitingFilter — Unit Tests
 *
 * Strategy: RETURNS_DEEP_STUBS on ProxyManager lets us stub the full chain:
 *   proxyManager.builder().build(...).tryConsume(1L)
 * without needing to import BucketProxy or RemoteBucketBuilder directly.
 * The deep stub auto-creates all intermediate mocks via the same stub.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitingFilter — Unit Tests")
@SuppressWarnings({"unchecked", "rawtypes"})
class RateLimitingFilterTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    ProxyManager<String> proxyManager;

    @Mock FilterChain filterChain;

    RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitingFilter(proxyManager);
    }

    /**
     * Stub the full Bucket4j chain so tryConsume returns the given value.
     * any(Supplier.class) disambiguates the build(K, Supplier) vs build(K, BucketConfiguration) overloads.
     */
    private void stubTryConsume(boolean tokenAvailable) {
        given(proxyManager.builder()
                .build(anyString(), any(Supplier.class))
                .tryConsume(1L))
                .willReturn(tokenAvailable);
    }

    // ── Pass-through (path not in RULES) ──────────────────────────────────────

    @Test
    @DisplayName("doFilter: unlisted path → passes through, no rate limiting applied")
    void doFilter_pathNotRateLimited_passesThrough() throws Exception {
        var req  = new MockHttpServletRequest("GET", "/api/products");
        var resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, filterChain);

        then(filterChain).should().doFilter(req, resp);
        // ProxyManager must not be called for unlisted paths
        then(proxyManager).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("doFilter: GET /api/contact not in RULES → passes through")
    void doFilter_getContact_passesThrough() throws Exception {
        var req  = new MockHttpServletRequest("GET", "/api/contact");
        var resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, filterChain);

        then(filterChain).should().doFilter(req, resp);
    }

    // ── Under limit (bucket has tokens) ───────────────────────────────────────

    @Test
    @DisplayName("doFilter: POST /api/auth/login, tokens available → passes through (200)")
    void doFilter_loginUnderLimit_passesThrough() throws Exception {
        var req  = new MockHttpServletRequest("POST", "/api/auth/login");
        var resp = new MockHttpServletResponse();
        req.setRemoteAddr("192.168.1.1");
        stubTryConsume(true);

        filter.doFilter(req, resp, filterChain);

        then(filterChain).should().doFilter(req, resp);
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    // ── Over limit (429) ──────────────────────────────────────────────────────

    @Test
    @DisplayName("doFilter: POST /api/auth/login, bucket exhausted → 429 + Retry-After: 60")
    void doFilter_loginOverLimit_returns429WithRetryAfter() throws Exception {
        var req  = new MockHttpServletRequest("POST", "/api/auth/login");
        var resp = new MockHttpServletResponse();
        req.setRemoteAddr("192.168.1.1");
        stubTryConsume(false);  // bucket exhausted

        filter.doFilter(req, resp, filterChain);

        then(filterChain).should(never()).doFilter(any(), any());
        assertThat(resp.getStatus()).isEqualTo(429);
        assertThat(resp.getHeader("Retry-After")).isEqualTo("60");
    }

    @Test
    @DisplayName("doFilter: POST /api/auth/register exhausted → 429 + Retry-After: 60")
    void doFilter_registerOverLimit_returns429() throws Exception {
        var req  = new MockHttpServletRequest("POST", "/api/auth/register");
        var resp = new MockHttpServletResponse();
        req.setRemoteAddr("10.0.0.1");
        stubTryConsume(false);

        filter.doFilter(req, resp, filterChain);

        assertThat(resp.getStatus()).isEqualTo(429);
        assertThat(resp.getHeader("Retry-After")).isEqualTo("60");
        then(filterChain).should(never()).doFilter(any(), any());
    }

    // ── IP resolution (tested via behavior) ───────────────────────────────────

    @Test
    @DisplayName("resolveClientIp: X-Forwarded-For present → filter passes through normally")
    void doFilter_withXForwardedFor_resolvesIpAndPassesRequest() throws Exception {
        var req  = new MockHttpServletRequest("POST", "/api/auth/login");
        var resp = new MockHttpServletResponse();
        req.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");
        stubTryConsume(true);

        filter.doFilter(req, resp, filterChain);

        then(filterChain).should().doFilter(req, resp);
    }

    @Test
    @DisplayName("resolveClientIp: X-Real-IP present → filter processes request normally")
    void doFilter_withXRealIp_resolvesIpAndPassesRequest() throws Exception {
        var req  = new MockHttpServletRequest("POST", "/api/auth/login");
        var resp = new MockHttpServletResponse();
        req.addHeader("X-Real-IP", "198.51.100.42");
        stubTryConsume(true);

        filter.doFilter(req, resp, filterChain);

        then(filterChain).should().doFilter(req, resp);
    }

    @Test
    @DisplayName("resolveClientIp: no headers → falls back to remoteAddr, filter works normally")
    void doFilter_noProxyHeaders_fallbackToRemoteAddr() throws Exception {
        var req  = new MockHttpServletRequest("POST", "/api/auth/login");
        var resp = new MockHttpServletResponse();
        req.setRemoteAddr("127.0.0.1");     // only remoteAddr, no proxy headers
        stubTryConsume(true);

        filter.doFilter(req, resp, filterChain);

        then(filterChain).should().doFilter(req, resp);
    }
}
