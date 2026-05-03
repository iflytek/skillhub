package com.iflytek.skillhub.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.security.AuthFailureThrottleService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class RedisAuthFailureThrottleRuntimeSelectionTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
            .withUserConfiguration(AuthFailureThrottleService.class);

    @Test
    void redisProvider_wiresRedisBackedAuthFailureThrottleAndPersistsCounters() {
        contextRunner
                .withInitializer(context -> applyRuntimeStateDefaults((ConfigurableEnvironment) context.getEnvironment()))
                .withPropertyValues(
                        "skillhub.runtime.state.provider=redis",
                        "spring.data.redis.host=" + REDIS.getHost(),
                        "spring.data.redis.port=" + REDIS.getMappedPort(6379)
                )
                .run(context -> {
                    assertThat(context.getEnvironment().getProperty("skillhub.auth.failure-throttle.mode"))
                            .isEqualTo("redis");

                    AuthFailureThrottleService service = context.getBean(AuthFailureThrottleService.class);
                    StringRedisTemplate redisTemplate = context.getBean(StringRedisTemplate.class);
                    String identifierKey = "auth-failure:local:id:alice";
                    String ipKey = "auth-failure:local:ip:203.0.113.10";
                    redisTemplate.delete(identifierKey);
                    redisTemplate.delete(ipKey);

                    for (int i = 0; i < 8; i++) {
                        service.recordFailure("local", "Alice", "203.0.113.10");
                    }

                    assertThat(redisTemplate.opsForValue().get(identifierKey)).isEqualTo("8");
                    assertThat(redisTemplate.getExpire(identifierKey)).isPositive();
                    assertThatThrownBy(() -> service.assertAllowed("local", "Alice", "198.51.100.1"))
                            .isInstanceOf(AuthFlowException.class);
                });
    }

    @Test
    void memoryProvider_keepsInMemoryAuthFailureThrottleAvailableAsFallback() {
        contextRunner
                .withInitializer(context -> applyRuntimeStateDefaults((ConfigurableEnvironment) context.getEnvironment()))
                .withPropertyValues("skillhub.runtime.state.provider=memory")
                .run(context -> {
                    assertThat(context.getEnvironment().getProperty("skillhub.auth.failure-throttle.mode"))
                            .isEqualTo("memory");

                    AuthFailureThrottleService service = context.getBean(AuthFailureThrottleService.class);
                    for (int i = 0; i < 8; i++) {
                        service.recordFailure("local", "Alice", "203.0.113.10");
                    }

                    assertThatThrownBy(() -> service.assertAllowed("local", "Alice", "198.51.100.1"))
                            .isInstanceOf(AuthFlowException.class);
                });
    }

    private void applyRuntimeStateDefaults(ConfigurableEnvironment environment) {
        environment.getPropertySources().addFirst(new MapPropertySource(
                RuntimeStatePropertyDefaults.PROPERTY_SOURCE_NAME,
                RuntimeStatePropertyDefaults.resolveOverrides(environment)
        ));
    }
}
