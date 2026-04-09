package backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

/**
 * Azure App Service F1 (Free tier) keep-alive scheduler.
 *
 * <p>F1 tier does not support "Always On" — the app sleeps after ~20 minutes
 * of no inbound HTTP traffic. This scheduler pings the local health endpoint
 * every 14 minutes to prevent idle shutdown.</p>
 *
 * <p>Enable in Azure App Service → Configuration → App settings:</p>
 * <pre>
 *   KEEP_ALIVE_ENABLED = true
 * </pre>
 * <p>Disabled locally by default (KEEP_ALIVE_ENABLED is absent → false).</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.keep-alive.enabled", havingValue = "true")
public class KeepAliveScheduler {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.keep-alive.url:http://localhost:${server.port:8080}/actuator/health}")
    private String healthUrl;

    @PostConstruct
    public void init() {
        log.info("[KeepAlive] ENABLED — will ping '{}' every 14 minutes to prevent Azure F1 idle-sleep", healthUrl);
    }

    /**
     * Ping every 14 minutes — safely under Azure F1's 20-minute idle timeout.
     * fixedDelay (not fixedRate) ensures no overlap if the request hangs.
     * initialDelay of 5 minutes lets the app fully start before first ping.
     */
    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "PT14M")
    public void keepAlive() {
        long start = System.currentTimeMillis();
        try {
            ResponseEntity<String> res = restTemplate.getForEntity(healthUrl, String.class);
            long ms = System.currentTimeMillis() - start;
            log.info("[KeepAlive] OK — HTTP {} | {}ms", res.getStatusCode().value(), ms);
        } catch (Exception ex) {
            long ms = System.currentTimeMillis() - start;
            log.warn("[KeepAlive] FAILED after {}ms — {}", ms, ex.getMessage());
        }
    }
}
