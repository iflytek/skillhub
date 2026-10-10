package com.iflytek.skillhub;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@Configuration
public class TestRedisConfig {

    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        return Mockito.mock(RedisConnectionFactory.class);
    }

    @SuppressWarnings("unchecked")
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate() {
        return Mockito.mock(RedisTemplate.class);
    }

    @Bean
    @Primary
    public StringRedisTemplate stringRedisTemplate() {
        StringRedisTemplate template = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        Map<String, String> values = new ConcurrentHashMap<>();
        Map<String, Instant> expirations = new ConcurrentHashMap<>();

        when(template.opsForValue()).thenReturn(valueOps);

        when(valueOps.get(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            evictExpired(values, expirations, key);
            return values.get(key);
        });

        when(valueOps.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            evictExpired(values, expirations, key);
            long next = Long.parseLong(values.getOrDefault(key, "0")) + 1L;
            values.put(key, Long.toString(next));
            return next;
        });

        doAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            String value = invocation.getArgument(1, String.class);
            Long timeout = invocation.getArgument(2, Long.class);
            TimeUnit unit = invocation.getArgument(3, TimeUnit.class);
            values.put(key, value);
            expirations.put(key, Instant.now().plusMillis(unit.toMillis(timeout)));
            return null;
        }).when(valueOps).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        when(template.delete(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            boolean removed = values.remove(key) != null;
            expirations.remove(key);
            return removed;
        });

        when(template.expire(anyString(), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            java.time.Duration ttl = invocation.getArgument(1, java.time.Duration.class);
            if (!values.containsKey(key)) {
                return false;
            }
            expirations.put(key, Instant.now().plus(ttl));
            return true;
        });

        when(template.getExpire(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            evictExpired(values, expirations, key);
            Instant expiresAt = expirations.get(key);
            if (expiresAt == null) {
                return -1L;
            }
            long seconds = java.time.Duration.between(Instant.now(), expiresAt).getSeconds();
            return Math.max(seconds, -1L);
        });

        return template;
    }

    private static void evictExpired(Map<String, String> values,
                                     Map<String, Instant> expirations,
                                     String key) {
        Instant expiresAt = expirations.get(key);
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            values.remove(key);
            expirations.remove(key);
        }
    }
}
