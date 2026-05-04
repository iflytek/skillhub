package com.iflytek.skillhub.auth.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Provides the shared Redis template used by authentication and other cross-cutting services.
 */
@Configuration
public class RedisTemplateConfig {

    @Bean(name = "skillhubRedisTemplate")
    @Primary
    @Lazy
    public RedisTemplate<String, Object> redisTemplate(
            ObjectProvider<RedisConnectionFactory> connectionFactoryProvider,
            ObjectMapper objectMapper) {
        RedisConnectionFactory connectionFactory = connectionFactoryProvider.getIfAvailable();
        if (connectionFactory == null) {
            throw new IllegalStateException("skillhubRedisTemplate requires RedisConnectionFactory when instantiated");
        }
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        ObjectMapper redisObjectMapper = objectMapper.copy();
        redisObjectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY
        );
        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer(redisObjectMapper);

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
