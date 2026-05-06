package backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Fails fast on startup if required env vars are missing or still set to placeholder defaults.
 * Only active in the "prod" profile — dev/test environments are not affected.
 */
@Component
@Profile("prod")
@Slf4j
public class ConfigValidator implements ApplicationRunner {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${supabase.service-role-key}")
    private String supabaseServiceRoleKey;

    @Value("${payos.checksum-key}")
    private String payosChecksumKey;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    // Substrings that indicate a value was never replaced from the template
    private static final String[] PLACEHOLDERS = {
        "your-", "tapo-secret-key", "CHANGE_ME", "change-me", "password"
    };

    @Override
    public void run(ApplicationArguments args) {
        List<String> violations = new ArrayList<>();

        checkPlaceholder("JWT_SECRET",                jwtSecret,               violations);
        checkPlaceholder("DB_PASSWORD",               dbPassword,               violations);
        checkPlaceholder("SUPABASE_SERVICE_ROLE_KEY", supabaseServiceRoleKey,   violations);
        checkPlaceholder("PAYOS_CHECKSUM_KEY",        payosChecksumKey,         violations);

        if (mailUsername == null || mailUsername.isBlank()) {
            violations.add("MAIL_USERNAME must not be empty");
        }

        if (!violations.isEmpty()) {
            violations.forEach(msg -> log.error("[CONFIG] {}", msg));
            throw new IllegalStateException(
                "Production startup aborted — insecure or missing config: " + violations
            );
        }

        log.info("[CONFIG] All required production config values validated OK.");
    }

    private void checkPlaceholder(String name, String value, List<String> violations) {
        if (value == null || value.isBlank()) {
            violations.add(name + " must not be empty");
            return;
        }
        String lower = value.toLowerCase();
        for (String placeholder : PLACEHOLDERS) {
            if (lower.contains(placeholder.toLowerCase())) {
                violations.add(name + " is still set to a placeholder value — provide a real value");
                return;
            }
        }
    }
}
