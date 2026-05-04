package com.iflytek.skillhub.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.BeanCreationException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

class RedisTemplateConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DefaultRedisTemplateConfig.class, RedisTemplateConfig.class);

    @Test
    void lazyTypedRedisTemplateDoesNotBreakContextsWithoutRedisConnectionFactory() {
        new ApplicationContextRunner()
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withUserConfiguration(RedisTemplateConfig.class)
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThatThrownBy(() -> context.getBean("skillhubRedisTemplate"))
                            .isInstanceOf(BeanCreationException.class)
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasRootCauseMessage("skillhubRedisTemplate requires RedisConnectionFactory when instantiated");
                });
    }

    @Test
    void createsTypedRedisTemplateEvenWhenDefaultRedisTemplateBeanExists() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("redisTemplate");
            assertThat(context).hasBean("skillhubRedisTemplate");

            @SuppressWarnings("unchecked")
            RedisTemplate<String, Object> typedTemplate =
                    (RedisTemplate<String, Object>) context.getBeanProvider(
                            ResolvableType.forClassWithGenerics(RedisTemplate.class, String.class, Object.class)
                    ).getIfAvailable();

            assertThat(typedTemplate).isNotNull();
            assertThat(typedTemplate.getConnectionFactory()).isNotNull();
            assertThat(typedTemplate.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
            assertThat(typedTemplate.getHashKeySerializer()).isInstanceOf(StringRedisSerializer.class);
            assertThat(typedTemplate.getValueSerializer()).isInstanceOf(GenericJackson2JsonRedisSerializer.class);
            assertThat(typedTemplate.getHashValueSerializer()).isInstanceOf(GenericJackson2JsonRedisSerializer.class);
        });
    }

    @Configuration
    static class DefaultRedisTemplateConfig {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return mock(RedisConnectionFactory.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean(name = "redisTemplate")
        RedisTemplate<Object, Object> defaultRedisTemplate(RedisConnectionFactory connectionFactory) {
            RedisTemplate<Object, Object> template = new RedisTemplate<>();
            template.setConnectionFactory(connectionFactory);
            template.afterPropertiesSet();
            return template;
        }
    }
}
