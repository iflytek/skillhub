package com.iflytek.skillhub.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.ratelimit.InMemorySlidingWindowRateLimiter;
import com.iflytek.skillhub.ratelimit.RateLimiter;
import com.iflytek.skillhub.ratelimit.RedisSlidingWindowRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class RedisRateLimiterRuntimeSelectionTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
            .withUserConfiguration(
                    InMemorySlidingWindowRateLimiter.class,
                    RedisSlidingWindowRateLimiter.class
            );

    @Test
    void redisProvider_wiresRedisRateLimiterAndUsesRedisBackedWindow() {
        contextRunner
                .withInitializer(context -> applyRuntimeStateDefaults((ConfigurableEnvironment) context.getEnvironment()))
                .withPropertyValues(
                        "skillhub.runtime.state.provider=redis",
                        "spring.data.redis.host=" + REDIS.getHost(),
                        "spring.data.redis.port=" + REDIS.getMappedPort(6379)
                )
                .run(context -> {
                    assertThat(context.getEnvironment().getProperty("skillhub.ratelimit.mode")).isEqualTo("redis");
                    assertThat(context.getBean(RateLimiter.class)).isInstanceOf(RedisSlidingWindowRateLimiter.class);

                    RateLimiter rateLimiter = context.getBean(RateLimiter.class);
                    String key = "skillhub:test:ratelimit:" + System.nanoTime();
                    assertThat(rateLimiter.tryAcquire(key, 2, 60)).isTrue();
                    assertThat(rateLimiter.tryAcquire(key, 2, 60)).isTrue();
                    assertThat(rateLimiter.tryAcquire(key, 2, 60)).isFalse();
                });
    }

    @Test
    void memoryProvider_keepsInMemoryRateLimiterAvailableAsFallback() {
        contextRunner
                .withInitializer(context -> applyRuntimeStateDefaults((ConfigurableEnvironment) context.getEnvironment()))
                .withPropertyValues("skillhub.runtime.state.provider=memory")
                .run(context -> {
                    assertThat(context.getEnvironment().getProperty("skillhub.ratelimit.mode")).isEqualTo("memory");
                    assertThat(context.getBean(RateLimiter.class)).isInstanceOf(InMemorySlidingWindowRateLimiter.class);
                });
    }

    private void applyRuntimeStateDefaults(ConfigurableEnvironment environment) {
        environment.getPropertySources().addFirst(new MapPropertySource(
                RuntimeStatePropertyDefaults.PROPERTY_SOURCE_NAME,
                RuntimeStatePropertyDefaults.resolveOverrides(environment)
        ));
    }
}
