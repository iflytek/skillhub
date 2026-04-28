package com.iflytek.skillhub.security;

import com.iflytek.skillhub.auth.exception.AuthFlowException;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Tracks repeated authentication failures and throttles abusive identifiers or client addresses.
 */
@Service
public class AuthFailureThrottleService {

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int IDENTIFIER_LIMIT = 8;
    private static final int IP_LIMIT = 30;

    private final StringRedisTemplate redisTemplate;
    private final String mode;
    private final ConcurrentHashMap<String, Deque<Long>> inMemoryFailures = new ConcurrentHashMap<>();

    public AuthFailureThrottleService(ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                      @Value("${skillhub.auth.failure-throttle.mode:redis}") String mode) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.mode = mode;
    }

    public void assertAllowed(String category, String identifier, String clientIp) {
        if (isLimited(identifierKey(category, identifier), IDENTIFIER_LIMIT)
                || isLimited(ipKey(category, clientIp), IP_LIMIT)) {
            throw new AuthFlowException(HttpStatus.TOO_MANY_REQUESTS, "error.auth.login.throttled", remainingMinutes(category, identifier, clientIp));
        }
    }

    public void recordFailure(String category, String identifier, String clientIp) {
        increment(identifierKey(category, identifier));
        increment(ipKey(category, clientIp));
    }

    public void resetIdentifier(String category, String identifier) {
        String key = identifierKey(category, identifier);
        if (key != null) {
            if (useMemoryMode()) {
                inMemoryFailures.remove(key);
            } else if (redisTemplate != null) {
                redisTemplate.delete(key);
            }
        }
    }

    private boolean isLimited(String key, int limit) {
        if (key == null) {
            return false;
        }
        if (useMemoryMode()) {
            Deque<Long> timestamps = inMemoryFailures.get(key);
            if (timestamps == null) {
                return false;
            }
            synchronized (timestamps) {
                evictExpired(timestamps);
                return timestamps.size() >= limit;
            }
        }
        if (redisTemplate == null) {
            return false;
        }
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return false;
        }
        try {
            return Integer.parseInt(value) >= limit;
        } catch (NumberFormatException ignored) {
            redisTemplate.delete(key);
            return false;
        }
    }

    private void increment(String key) {
        if (key == null) {
            return;
        }
        if (useMemoryMode()) {
            Deque<Long> timestamps = inMemoryFailures.computeIfAbsent(key, ignored -> new ConcurrentLinkedDeque<>());
            synchronized (timestamps) {
                evictExpired(timestamps);
                timestamps.addLast(Instant.now().toEpochMilli());
            }
            return;
        }
        if (redisTemplate == null) {
            return;
        }
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, WINDOW);
        }
    }

    private long remainingMinutes(String category, String identifier, String clientIp) {
        long identifierMinutes = remainingMinutes(identifierKey(category, identifier));
        long ipMinutes = remainingMinutes(ipKey(category, clientIp));
        return Math.max(1, Math.max(identifierMinutes, ipMinutes));
    }

    private long remainingMinutes(String key) {
        if (key == null) {
            return 1;
        }
        if (useMemoryMode()) {
            Deque<Long> timestamps = inMemoryFailures.get(key);
            if (timestamps == null) {
                return 1;
            }
            synchronized (timestamps) {
                evictExpired(timestamps);
                Long oldest = timestamps.peekFirst();
                if (oldest == null) {
                    return 1;
                }
                long remainingMillis = (oldest + WINDOW.toMillis()) - Instant.now().toEpochMilli();
                return Math.max(1, (remainingMillis + 59_999) / 60_000);
            }
        }
        if (redisTemplate == null) {
            return 1;
        }
        Long seconds = redisTemplate.getExpire(key);
        if (seconds == null || seconds <= 0) {
            return 1;
        }
        return Math.max(1, (seconds + 59) / 60);
    }

    private String identifierKey(String category, String identifier) {
        if (!StringUtils.hasText(identifier)) {
            return null;
        }
        return "auth-failure:" + category + ":id:" + identifier.trim().toLowerCase(Locale.ROOT);
    }

    private String ipKey(String category, String clientIp) {
        if (!StringUtils.hasText(clientIp)) {
            return null;
        }
        return "auth-failure:" + category + ":ip:" + clientIp.trim();
    }

    private boolean useMemoryMode() {
        return "memory".equalsIgnoreCase(mode);
    }

    private void evictExpired(Deque<Long> timestamps) {
        long threshold = Instant.now().toEpochMilli() - WINDOW.toMillis();
        while (!timestamps.isEmpty()) {
            Long oldest = timestamps.peekFirst();
            if (oldest == null || oldest >= threshold) {
                return;
            }
            timestamps.pollFirst();
        }
    }
}
