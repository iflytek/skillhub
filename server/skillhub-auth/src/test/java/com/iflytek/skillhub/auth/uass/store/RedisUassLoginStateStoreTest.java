package com.iflytek.skillhub.auth.uass.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisUassLoginStateStoreTest {

    @Test
    void save_persistsStateUsingRedisKeyPrefixAndConfiguredTtl() {
        RedisTemplate<String, Object> redisTemplate = redisTemplate();
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        RedisUassLoginStateStore store = new RedisUassLoginStateStore(redisTemplate, Duration.ofMinutes(5));
        UassLoginState loginState = loginState();

        store.save("state-1", loginState);

        verify(valueOperations).set("uass:state:state-1", loginState, 300_000L, TimeUnit.MILLISECONDS);
    }

    @Test
    void find_andDelete_roundTripStoredState() {
        RedisTemplate<String, Object> redisTemplate = redisTemplate();
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        UassLoginState loginState = loginState();
        when(valueOperations.get("uass:state:state-1")).thenReturn(loginState);
        RedisUassLoginStateStore store = new RedisUassLoginStateStore(redisTemplate, Duration.ofMinutes(5));

        assertThat(store.find("state-1")).contains(loginState);

        store.delete("state-1");

        verify(redisTemplate).delete("uass:state:state-1");
    }

    @Test
    void consume_returnsStateAndDeletesRedisKey() {
        RedisTemplate<String, Object> redisTemplate = redisTemplate();
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        UassLoginState loginState = loginState();
        when(valueOperations.get("uass:state:state-1")).thenReturn(loginState);
        RedisUassLoginStateStore store = new RedisUassLoginStateStore(redisTemplate, Duration.ofMinutes(5));

        assertThat(store.consume("state-1")).contains(loginState);

        verify(redisTemplate).delete("uass:state:state-1");
    }

    @Test
    void find_discardsUnexpectedRedisPayloadTypes() {
        RedisTemplate<String, Object> redisTemplate = redisTemplate();
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("unexpected");
        RedisUassLoginStateStore store = new RedisUassLoginStateStore(redisTemplate, Duration.ofMinutes(5));

        assertThat(store.find("state-1")).isEmpty();

        verify(redisTemplate).delete("uass:state:state-1");
    }

    @SuppressWarnings("unchecked")
    private static RedisTemplate<String, Object> redisTemplate() {
        return mock(RedisTemplate.class);
    }

    private static UassLoginState loginState() {
        return new UassLoginState("/dashboard", Instant.parse("2026-04-29T15:00:00Z"), "uass", "fingerprint-1");
    }
}
