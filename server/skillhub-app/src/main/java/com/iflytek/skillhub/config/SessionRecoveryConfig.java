package com.iflytek.skillhub.config;

import com.iflytek.skillhub.auth.session.CorruptSessionRemover;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Connects the authentication module's recovery boundary to Spring Session storage.
 */
@Configuration
public class SessionRecoveryConfig {

    @Bean
    CorruptSessionRemover corruptSessionRemover(
            StringRedisTemplate redisTemplate,
            @Value("${spring.session.redis.namespace:spring:session}") String namespace) {
        String keyPrefix = namespace.endsWith(":") ? namespace : namespace + ":";
        return sessionId -> redisTemplate.delete(List.of(
                keyPrefix + "sessions:" + sessionId,
                keyPrefix + "sessions:expires:" + sessionId
        ));
    }
}
