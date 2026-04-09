package backend.config;

import backend.security.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket + STOMP configuration — java-pro: pure Spring WebSocket, no external broker.
 *
 * <p>Topology:
 * <ul>
 *   <li>Endpoint: {@code /ws} (SockJS fallback enabled)</li>
 *   <li>Admin topic: {@code /topic/admin} — order/return/contact notifications</li>
 *   <li>App prefix: {@code /app} — client → server messages (reserved for future use)</li>
 * </ul>
 *
 * <p>Security: {@link StompAuthChannelInterceptor} validates JWT on every CONNECT frame.
 * Only ADMIN/STAFF roles can establish a STOMP session.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // In-memory broker — lightweight, no external dependency
        registry.enableSimpleBroker("/topic", "/user");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")   // CORS: restrict to APP_BASE_URL in prod via SecurityConfig
                .withSockJS();                   // SockJS fallback for HTTP polling
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // java-pro: intercept CONNECT frame -> validate JWT -> set principal
        registration.interceptors(stompAuthInterceptor);
    }
}
