package backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Azure App Service F1 (Free tier) keep-alive scheduler.
 *
 * <p>F1 tier does not support "Always On" — the app sleeps after ~20 minutes
 * of no inbound HTTP traffic. This scheduler pings the local health endpoint
 * every 14 minutes to prevent idle shutdown.</p>
 *
 * <p>Enable via {@code app.keep-alive.enabled=true} in application.yml
 * (set only in the Azure / production profile — disabled locally by default).</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.keep-alive.enabled", havingValue = "true")
public class KeepAliveScheduler {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.keep-alive.url:http://localhost:${server.port:8080}/actuator/health}")
    private String healthUrl;

    /**
     * Ping every 14 minutes (840_000 ms) — well under the 20-minute Azure F1 idle timeout.
     * initialDelay = 5 minutes so it doesn't fire immediately on startup.
     */
    @Scheduled(initialDelay = 300_000, fixedDelay = 840_000)
    public void keepAlive() {
        try {
            String response = restTemplate.getForObject(healthUrl, String.class);
            log.debug("[KeepAlive] Self-ping OK → {}", healthUrl);
        } catch (Exception ex) {
            // Non-critical — log at WARN so we know if health endpoint is down
            log.warn("[KeepAlive] Self-ping failed: {} — {}", healthUrl, ex.getMessage());
        }
    }
}
