package com.iflytek.skillhub.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RedisSentinelProfileConfigurationTest {

    private final PropertySource<?> sentinelProfile = loadSentinelProfile();

    @Test
    void separateDataAndSentinelPasswordsResolveIndependently() {
        assertThat(resolve("spring.data.redis.password", Map.of(
                "SPRING_DATA_REDIS_PASSWORD", "data-password",
                "SPRING_DATA_REDIS_SENTINEL_PASSWORD", "sentinel-password"
        ))).isEqualTo("data-password");

        assertThat(resolve("spring.data.redis.sentinel.password", Map.of(
                "SPRING_DATA_REDIS_PASSWORD", "data-password",
                "SPRING_DATA_REDIS_SENTINEL_PASSWORD", "sentinel-password"
        ))).isEqualTo("sentinel-password");
    }

    @Test
    void sentinelPasswordRemainsADataPasswordFallback() {
        assertThat(resolve("spring.data.redis.password", Map.of(
                "SPRING_DATA_REDIS_SENTINEL_PASSWORD", "legacy-password"
        ))).isEqualTo("legacy-password");
    }

    private String resolve(String propertyName, Map<String, Object> environment) {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(new MapPropertySource("test-environment", environment));
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(sources);
        return resolver.resolveRequiredPlaceholders((String) sentinelProfile.getProperty(propertyName));
    }

    private static PropertySource<?> loadSentinelProfile() {
        try {
            return new YamlPropertySourceLoader()
                    .load("redis-sentinel", new ClassPathResource("application-redis-sentinel.yml"))
                    .getFirst();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load Redis Sentinel profile", e);
        }
    }
}
