package backend.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * StompAuthChannelInterceptor — java-pro: WebSocket security gate.
 *
 * <p>Intercepts STOMP {@code CONNECT} frames và xác thực JWT token.
 * Reuses {@link JwtTokenProvider} và {@link CustomUserDetailsService} —
 * không duplicate JWT parsing logic.
 *
 * <p>Tất cả authenticated user (ROLE_USER, ROLE_ADMIN, ROLE_STAFF) đều được phép kết nối.
 * Authorization theo topic được STOMP message broker tự xử lý tại tầng subscribe.
 * - Admin: subscribe {@code /topic/admin/*}
 * - User: subscribe {@code /user/queue/notifications} (user-specific via convertAndSendToUser)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider       jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // Chỉ validate lúc CONNECT — subscribe/send kế thừa principal đã set
        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");

        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("[WS] STOMP CONNECT rejected: missing or invalid Authorization header");
            throw new IllegalArgumentException("WebSocket: Authorization header required");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        if (!jwtTokenProvider.validateToken(token)) {
            log.warn("[WS] STOMP CONNECT rejected: invalid or expired JWT");
            throw new IllegalArgumentException("WebSocket: Invalid or expired token");
        }

        String email = jwtTokenProvider.getEmailFromToken(token);
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);

        // Set principal — all authenticated roles (USER/ADMIN/STAFF) may connect.
        // Topic-level access control is handled by the broker and subscription paths.
        var auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        accessor.setUser(auth);

        log.debug("[WS] STOMP CONNECT authenticated: {} roles={}", email,
                userDetails.getAuthorities());
        return message;
    }
}

