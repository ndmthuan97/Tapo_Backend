package backend.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * Redis Cache configuration — Upstash TLS.
 *
 * <p>Parses {@code rediss://} URL manually so credentials and SSL are handled
 * even when Spring Boot's URI-based auto-config strips them.
 *
 * <p>Cache TTLs (java-pro: map.of for name→config):
 * <ul>
 *   <li>dashboard   → 5  min (KPI stats)</li>
 *   <li>products    → 2  min (price/stock)</li>
 *   <li>topProducts → 10 min (aggregate)</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class RedisConfig {

    @Value("${spring.data.redis.url}")
    private String redisUrl;

    // ── Connection ────────────────────────────────────────────────────────────────

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        URI uri = URI.create(redisUrl);
        boolean useSsl = redisUrl.startsWith("rediss://");

        String userInfo = uri.getUserInfo();
        String password = (userInfo != null && userInfo.contains(":"))
                ? userInfo.substring(userInfo.indexOf(':') + 1)
                : userInfo;

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(uri.getHost());
        config.setPort(uri.getPort());
        if (password != null && !password.isBlank()) config.setPassword(password);
        if (userInfo != null && userInfo.contains(":")) {
            config.setUsername(userInfo.substring(0, userInfo.indexOf(':')));
        }

        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder =
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(10));
        if (useSsl) builder.useSsl().disablePeerVerification();

        return new LettuceConnectionFactory(config, builder.build());
    }

    // ── Templates ─────────────────────────────────────────────────────────────────

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer());
        template.setHashValueSerializer(jsonSerializer());
        template.afterPropertiesSet();
        return template;
    }

    // ── Cache Manager ─────────────────────────────────────────────────────────────

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer()))
                .disableCachingNullValues();

        return RedisCacheManager.builder(factory)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(Map.of(
                        "dashboard",      base.entryTtl(Duration.ofMinutes(5)),
                        "products",       base.entryTtl(Duration.ofMinutes(2)),
                        "topProducts",    base.entryTtl(Duration.ofMinutes(10)),
                        // Sprint 2: expanded cache configs
                        "product-detail", base.entryTtl(Duration.ofMinutes(5)),
                        "metadata",       base.entryTtl(Duration.ofMinutes(30))
                ))
                .build();
    }

    // ── Bucket4j ProxyManager (Rate Limiting) ─────────────────────────────────────

    /**
     * Bucket4j Lettuce-based ProxyManager for distributed rate limiting.
     * Reuses Upstash Redis -- no extra infra needed.
     * java-pro: build RedisClient from same URL used by Spring Data Redis.
     */
    @Bean
    @SuppressWarnings("unchecked")
    public ProxyManager<String> bucketProxyManager() {
        // Lettuce CAS-based ProxyManager -- build dedicated client from same Upstash URL
        // Bucket4j 8.x internally uses byte[] keys; safe cast via builder pattern
        RedisClient redisClient = RedisClient.create(redisUrl);
        var rawProxy = LettuceBasedProxyManager.builderFor(redisClient).build();
        // java-pro: SuppressWarnings justified -- Bucket4j redis key serialization is opaque
        return (ProxyManager<String>) (ProxyManager<?>) rawProxy;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private static GenericJackson2JsonRedisSerializer jsonSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }
}
