package com.iflytek.skillhub.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.auth.config.RedisTemplateConfig;
import com.iflytek.skillhub.auth.uass.UassProperties;
import com.iflytek.skillhub.auth.uass.config.UassStateStoreConfiguration;
import com.iflytek.skillhub.auth.uass.store.LocalUassLoginStateStore;
import com.iflytek.skillhub.auth.uass.store.RedisUassLoginStateStore;
import com.iflytek.skillhub.auth.uass.store.UassLoginState;
import com.iflytek.skillhub.auth.uass.store.UassLoginStateStore;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class RedisUassStateRuntimeSelectionTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    private final ApplicationContextRunner baseContextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    UassRuntimeSelectionTestConfig.class,
                    UassStateStoreConfiguration.class
            );

    private final ApplicationContextRunner redisContextRunner = baseContextRunner
            .withUserConfiguration(
                    RealRedisConnectionTestConfig.class,
                    RedisTemplateConfig.class
            );

    @Test
    void redisProvider_wiresRedisBackedUassStateStoreAndPersistsStateAcrossContexts() {
        String state = "uass-state-" + System.nanoTime();
        UassLoginState loginState = loginState();

        redisContextRunner
                .withInitializer(context -> applyRuntimeStateDefaults((ConfigurableEnvironment) context.getEnvironment()))
                .withPropertyValues(redisPropertyValues())
                .run(context -> {
                    assertThat(context.getEnvironment().getProperty("skillhub.auth.uass.cache-mode"))
                            .isEqualTo("redis");
                    assertThat(context.getBean(UassLoginStateStore.class)).isInstanceOf(RedisUassLoginStateStore.class);

                    UassLoginStateStore store = context.getBean(UassLoginStateStore.class);
                    store.save(state, loginState);
                });

        redisContextRunner
                .withInitializer(context -> applyRuntimeStateDefaults((ConfigurableEnvironment) context.getEnvironment()))
                .withPropertyValues(redisPropertyValues())
                .run(context -> {
                    UassLoginStateStore store = context.getBean(UassLoginStateStore.class);

                    assertThat(store.find(state)).contains(loginState);
                    assertThat(store.consume(state)).contains(loginState);
                    assertThat(store.find(state)).isEmpty();
                });
    }

    @Test
    void memoryProvider_keepsLocalUassStateStoreAvailableAsFallback() {
        baseContextRunner
                .withInitializer(context -> applyRuntimeStateDefaults((ConfigurableEnvironment) context.getEnvironment()))
                .withPropertyValues(
                        "skillhub.runtime.state.provider=memory",
                        "skillhub.auth.uass.enabled=true"
                )
                .run(context -> {
                    assertThat(context.getEnvironment().getProperty("skillhub.auth.uass.cache-mode"))
                            .isEqualTo("local");
                    assertThat(context.getBean(UassLoginStateStore.class)).isInstanceOf(LocalUassLoginStateStore.class);
                });
    }

    private String[] redisPropertyValues() {
        return new String[] {
                "skillhub.runtime.state.provider=redis",
                "skillhub.auth.uass.enabled=true",
                "spring.data.redis.host=" + REDIS.getHost(),
                "spring.data.redis.port=" + REDIS.getMappedPort(6379)
        };
    }

    private void applyRuntimeStateDefaults(ConfigurableEnvironment environment) {
        environment.getPropertySources().addFirst(new MapPropertySource(
                RuntimeStatePropertyDefaults.PROPERTY_SOURCE_NAME,
                RuntimeStatePropertyDefaults.resolveOverrides(environment)
        ));
    }

    private static UassLoginState loginState() {
        return new UassLoginState(
                "/dashboard/publish",
                Instant.parse("2026-05-03T03:21:00Z"),
                "uass",
                "fingerprint-redis"
        );
    }

    @Configuration
    @EnableAutoConfiguration
    @EnableConfigurationProperties(UassProperties.class)
    static class UassRuntimeSelectionTestConfig {
    }

    @Configuration
    static class RealRedisConnectionTestConfig {

        @Bean(destroyMethod = "destroy")
        RedisConnectionFactory redisConnectionFactory(
                @Value("${spring.data.redis.host}") String host,
                @Value("${spring.data.redis.port}") int port) {
            LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(host, port);
            connectionFactory.afterPropertiesSet();
            return connectionFactory;
        }
    }
}
