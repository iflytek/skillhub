package com.iflytek.skillhub.config;

import com.iflytek.skillhub.auth.session.CorruptSessionRemover;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
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
        return sessionCookieValue -> {
            List<String> keys = new ArrayList<>();
            for (String sessionId : sessionIdCandidates(sessionCookieValue)) {
                keys.add(keyPrefix + "sessions:" + sessionId);
                keys.add(keyPrefix + "sessions:expires:" + sessionId);
            }
            redisTemplate.delete(keys);
        };
    }

    private List<String> sessionIdCandidates(String cookieValue) {
        List<String> candidates = new ArrayList<>();
        candidates.add(cookieValue);
        try {
            String decoded = new String(Base64.getDecoder().decode(cookieValue), StandardCharsets.UTF_8);
            if (!decoded.equals(cookieValue) && decoded.matches("[A-Za-z0-9._-]{1,128}")) {
                candidates.add(decoded);
            }
        } catch (IllegalArgumentException ignored) {
            // A custom CookieSerializer may store the raw session id without Base64 encoding.
        }
        return candidates;
    }
}
