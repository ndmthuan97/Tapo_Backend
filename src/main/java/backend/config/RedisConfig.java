package backend.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SslOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.URI;
import java.time.Duration;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.url}")
    private String redisUrl;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        URI uri = URI.create(redisUrl);
        boolean useSsl = redisUrl.startsWith("rediss://");

        // Parse credentials from "user:password" in the URI's userInfo
        String userInfo = uri.getUserInfo(); // "default:password"
        String password = (userInfo != null && userInfo.contains(":"))
                ? userInfo.substring(userInfo.indexOf(':') + 1)
                : userInfo;

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(uri.getHost());
        config.setPort(uri.getPort());
        if (password != null && !password.isBlank()) {
            config.setPassword(password);
        }
        // Upstash uses "default" as the username
        if (userInfo != null && userInfo.contains(":")) {
            config.setUsername(userInfo.substring(0, userInfo.indexOf(':')));
        }

        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder =
                LettuceClientConfiguration.builder()
                        .commandTimeout(Duration.ofSeconds(10));

        if (useSsl) {
            builder.useSsl().disablePeerVerification();
        }

        return new LettuceConnectionFactory(config, builder.build());
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
