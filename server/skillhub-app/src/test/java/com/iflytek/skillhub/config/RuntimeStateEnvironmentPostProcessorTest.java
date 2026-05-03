package com.iflytek.skillhub.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.uass.UassProperties;
import com.iflytek.skillhub.auth.uass.config.UassStateStoreConfiguration;
import com.iflytek.skillhub.auth.uass.store.LocalUassLoginStateStore;
import com.iflytek.skillhub.auth.uass.store.RedisUassLoginStateStore;
import com.iflytek.skillhub.auth.uass.store.UassLoginStateStore;
import com.iflytek.skillhub.ratelimit.InMemorySlidingWindowRateLimiter;
import com.iflytek.skillhub.ratelimit.RateLimiter;
import com.iflytek.skillhub.ratelimit.RedisSlidingWindowRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

class RuntimeStateEnvironmentPostProcessorTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    RuntimeStateTestConfig.class,
                    UassStateStoreConfiguration.class,
                    InMemorySlidingWindowRateLimiter.class
            );

    @Test
    void memoryProvider_wiresMemoryBackedBeansAndProperties() {
        contextRunner
                .withInitializer(context -> applyRuntimeStateDefaults((ConfigurableEnvironment) context.getEnvironment()))
                .withPropertyValues(
                        "skillhub.runtime.state.provider=memory",
                        "skillhub.auth.uass.enabled=true"
                )
                .run(context -> {
                    assertThat(context.getEnvironment().getProperty("spring.session.store-type")).isEqualTo("none");
                    assertThat(context.getEnvironment().getProperty("skillhub.ratelimit.mode")).isEqualTo("memory");
                    assertThat(context.getEnvironment().getProperty("skillhub.auth.failure-throttle.mode")).isEqualTo("memory");
                    assertThat(context.getEnvironment().getProperty("skillhub.auth.uass.cache-mode")).isEqualTo("local");
                    assertThat(context.getBean(RateLimiter.class)).isInstanceOf(InMemorySlidingWindowRateLimiter.class);
                    assertThat(context.getBean(UassLoginStateStore.class)).isInstanceOf(LocalUassLoginStateStore.class);
                });
    }

    @Test
    void redisProvider_wiresRedisBackedBeansAndProperties() {
        contextRunner
                .withUserConfiguration(AvailableRedisBeansTestConfig.class, RedisSlidingWindowRateLimiter.class)
                .withInitializer(context -> applyRuntimeStateDefaults((ConfigurableEnvironment) context.getEnvironment()))
                .withPropertyValues(
                        "skillhub.runtime.state.provider=redis",
                        "skillhub.auth.uass.enabled=true"
                )
                .run(context -> {
                    assertThat(context.getEnvironment().getProperty("spring.session.store-type")).isEqualTo("redis");
                    assertThat(context.getEnvironment().getProperty("skillhub.ratelimit.mode")).isEqualTo("redis");
                    assertThat(context.getEnvironment().getProperty("skillhub.auth.failure-throttle.mode")).isEqualTo("redis");
                    assertThat(context.getEnvironment().getProperty("skillhub.auth.uass.cache-mode")).isEqualTo("redis");
                    assertThat(context.getBean(RateLimiter.class)).isInstanceOf(RedisSlidingWindowRateLimiter.class);
                    assertThat(context.getBean(UassLoginStateStore.class)).isInstanceOf(RedisUassLoginStateStore.class);
                });
    }

    @Configuration
    @EnableConfigurationProperties(UassProperties.class)
    static class RuntimeStateTestConfig {
    }

    @Configuration
    static class AvailableRedisBeansTestConfig {

        @Bean
        StringRedisTemplate stringRedisTemplate() {
            StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
            RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
            RedisConnection connection = mock(RedisConnection.class);
            when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
            when(connectionFactory.getConnection()).thenReturn(connection);
            when(connection.ping()).thenReturn("PONG");
            return redisTemplate;
        }

        @Bean(name = "skillhubRedisTemplate")
        RedisTemplate<String, Object> redisTemplate() {
            RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
            RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
            RedisConnection connection = mock(RedisConnection.class);
            when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
            when(connectionFactory.getConnection()).thenReturn(connection);
            when(connection.ping()).thenReturn("PONG");
            return redisTemplate;
        }
    }

    private void applyRuntimeStateDefaults(ConfigurableEnvironment environment) {
        environment.getPropertySources().addFirst(
                new MapPropertySource(
                        RuntimeStatePropertyDefaults.PROPERTY_SOURCE_NAME,
                        RuntimeStatePropertyDefaults.resolveOverrides(environment)
                )
        );
    }
}
