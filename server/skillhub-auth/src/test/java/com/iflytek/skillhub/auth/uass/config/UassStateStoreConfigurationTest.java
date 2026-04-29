package com.iflytek.skillhub.auth.uass.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.iflytek.skillhub.auth.uass.UassProperties;
import com.iflytek.skillhub.auth.uass.store.LocalUassLoginStateStore;
import com.iflytek.skillhub.auth.uass.store.RedisUassLoginStateStore;
import com.iflytek.skillhub.auth.uass.store.UassLoginStateStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

class UassStateStoreConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(UassPropertiesTestConfig.class, UassStateStoreConfiguration.class);

    @Test
    void autoMode_usesLocalStoreWhenRedisTemplateIsUnavailable() {
        contextRunner
                .withPropertyValues("skillhub.auth.uass.cache-mode=auto")
                .run(context -> assertThat(context.getBean(UassLoginStateStore.class))
                        .isInstanceOf(LocalUassLoginStateStore.class));
    }

    @Test
    void autoMode_usesRedisStoreWhenRedisTemplateIsAvailable() {
        contextRunner
                .withUserConfiguration(RedisTemplateTestConfig.class)
                .withPropertyValues("skillhub.auth.uass.cache-mode=auto")
                .run(context -> assertThat(context.getBean(UassLoginStateStore.class))
                        .isInstanceOf(RedisUassLoginStateStore.class));
    }

    @Test
    void localMode_alwaysUsesLocalStore() {
        contextRunner
                .withUserConfiguration(RedisTemplateTestConfig.class)
                .withPropertyValues("skillhub.auth.uass.cache-mode=local")
                .run(context -> assertThat(context.getBean(UassLoginStateStore.class))
                        .isInstanceOf(LocalUassLoginStateStore.class));
    }

    @Configuration
    @EnableConfigurationProperties(UassProperties.class)
    static class UassPropertiesTestConfig {
    }

    @Configuration
    static class RedisTemplateTestConfig {

        @Bean
        RedisTemplate<String, Object> redisTemplate() {
            return mock(RedisTemplate.class);
        }
    }
}
