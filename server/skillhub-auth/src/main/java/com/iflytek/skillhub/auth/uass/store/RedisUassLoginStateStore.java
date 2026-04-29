package com.iflytek.skillhub.auth.uass.store;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.StringUtils;

public class RedisUassLoginStateStore implements UassLoginStateStore {

    static final String KEY_PREFIX = "uass:state:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ValueOperations<String, Object> valueOperations;
    private final Duration ttl;

    public RedisUassLoginStateStore(RedisTemplate<String, Object> redisTemplate, Duration ttl) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.valueOperations = redisTemplate.opsForValue();
        this.ttl = requirePositive(ttl);
    }

    @Override
    public void save(String state, UassLoginState loginState) {
        valueOperations.set(key(state), Objects.requireNonNull(loginState, "loginState must not be null"),
                ttl.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public Optional<UassLoginState> find(String state) {
        String key = key(state);
        Object value = valueOperations.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof UassLoginState loginState) {
            return Optional.of(loginState);
        }
        redisTemplate.delete(key);
        return Optional.empty();
    }

    @Override
    public Optional<UassLoginState> consume(String state) {
        String key = key(state);
        Optional<UassLoginState> loginState = find(state);
        redisTemplate.delete(key);
        return loginState;
    }

    @Override
    public void delete(String state) {
        redisTemplate.delete(key(state));
    }

    private static String key(String state) {
        if (!StringUtils.hasText(state)) {
            throw new IllegalArgumentException("state must not be blank");
        }
        return KEY_PREFIX + state.trim();
    }

    private static Duration requirePositive(Duration ttl) {
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        return ttl;
    }
}
