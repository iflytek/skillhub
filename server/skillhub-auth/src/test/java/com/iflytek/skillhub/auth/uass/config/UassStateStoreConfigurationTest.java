package com.iflytek.skillhub.auth.uass.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.uass.UassProperties;
import com.iflytek.skillhub.auth.uass.store.LocalUassLoginStateStore;
import com.iflytek.skillhub.auth.uass.store.RedisUassLoginStateStore;
import com.iflytek.skillhub.auth.uass.store.UassLoginStateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

@ExtendWith(OutputCaptureExtension.class)
class UassStateStoreConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(UassPropertiesTestConfig.class, UassStateStoreConfiguration.class);

    @Test
    void autoMode_usesLocalStoreWhenRedisTemplateIsUnavailable(CapturedOutput output) {
        contextRunner
                .withPropertyValues(
                        "skillhub.auth.uass.enabled=true",
                        "skillhub.auth.uass.cache-mode=auto"
                )
                .run(context -> assertThat(context.getBean(UassLoginStateStore.class))
                        .isInstanceOf(LocalUassLoginStateStore.class));

        assertThat(output).contains("LOCAL mode")
                .contains("not shared across replicas");
    }

    @Test
    void autoMode_usesRedisStoreWhenRedisTemplateIsAvailable() {
        contextRunner
                .withUserConfiguration(AvailableRedisTemplateTestConfig.class)
                .withPropertyValues(
                        "skillhub.auth.uass.enabled=true",
                        "skillhub.auth.uass.cache-mode=auto"
                )
                .run(context -> assertThat(context.getBean(UassLoginStateStore.class))
                        .isInstanceOf(RedisUassLoginStateStore.class));
    }

    @Test
    void autoMode_fallsBackToLocalStoreWhenRedisConnectionIsUnavailable(CapturedOutput output) {
        contextRunner
                .withUserConfiguration(UnavailableRedisTemplateTestConfig.class)
                .withPropertyValues(
                        "skillhub.auth.uass.enabled=true",
                        "skillhub.auth.uass.cache-mode=auto"
                )
                .run(context -> assertThat(context.getBean(UassLoginStateStore.class))
                        .isInstanceOf(LocalUassLoginStateStore.class));

        assertThat(output).contains("LOCAL mode")
                .contains("Redis is unavailable");
    }

    @Test
    void localMode_alwaysUsesLocalStore(CapturedOutput output) {
        contextRunner
                .withUserConfiguration(AvailableRedisTemplateTestConfig.class)
                .withPropertyValues(
                        "skillhub.auth.uass.enabled=true",
                        "skillhub.auth.uass.cache-mode=local"
                )
                .run(context -> assertThat(context.getBean(UassLoginStateStore.class))
                        .isInstanceOf(LocalUassLoginStateStore.class));

        assertThat(output).contains("LOCAL mode")
                .contains("cacheMode=local");
    }

    @Test
    void redisMode_usesRedisStoreWhenRedisTemplateIsAvailable() {
        contextRunner
                .withUserConfiguration(AvailableRedisTemplateTestConfig.class)
                .withPropertyValues(
                        "skillhub.auth.uass.enabled=true",
                        "skillhub.auth.uass.cache-mode=redis"
                )
                .run(context -> assertThat(context.getBean(UassLoginStateStore.class))
                        .isInstanceOf(RedisUassLoginStateStore.class));
    }

    @Test
    void redisMode_failsFastWhenRedisTemplateIsMissing() {
        contextRunner
                .withPropertyValues(
                        "skillhub.auth.uass.enabled=true",
                        "skillhub.auth.uass.cache-mode=redis"
                )
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNotNull();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("cacheMode=redis requires an available Redis connection");
                });
    }

    @Test
    void redisMode_failsFastWhenRedisConnectionIsUnavailable() {
        contextRunner
                .withUserConfiguration(UnavailableRedisTemplateTestConfig.class)
                .withPropertyValues(
                        "skillhub.auth.uass.enabled=true",
                        "skillhub.auth.uass.cache-mode=redis"
                )
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNotNull();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("cacheMode=redis requires an available Redis connection");
                });
    }

    @Configuration
    @EnableConfigurationProperties(UassProperties.class)
    static class UassPropertiesTestConfig {
    }

    @Configuration
    static class AvailableRedisTemplateTestConfig {

        @Bean
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

    @Configuration
    static class UnavailableRedisTemplateTestConfig {

        @Bean
        RedisTemplate<String, Object> redisTemplate() {
            RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
            RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
            when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
            when(connectionFactory.getConnection()).thenThrow(new RedisConnectionFailureException("redis down"));
            return redisTemplate;
        }
    }
}
