package com.iflytek.skillhub.ratelimit;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * Production rate limiter backed by Redis and a Lua script for atomic sliding-window checks.
 *
 * <p>Fail-open behavior: if Redis is unavailable, requests are allowed through and a warning
 * is logged. This trades rate-limit enforcement for service availability — preferable to
 * returning 500 on every request when Redis is down.
 */
@Component
@Profile("!test")
public class RedisSlidingWindowRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisSlidingWindowRateLimiter.class);

    private final StringRedisTemplate redisTemplate;
    private DefaultRedisScript<Long> rateLimitScript;

    public RedisSlidingWindowRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() {
        rateLimitScript = new DefaultRedisScript<>();
        rateLimitScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("ratelimit.lua")));
        rateLimitScript.setResultType(Long.class);
    }

    @Override
    public boolean tryAcquire(String key, int limit, int windowSeconds) {
        long now = System.currentTimeMillis();
        long windowMillis = windowSeconds * 1000L;

        try {
            Long result = redisTemplate.execute(
                    rateLimitScript,
                    Collections.singletonList(key),
                    String.valueOf(now),
                    String.valueOf(windowMillis),
                    String.valueOf(limit)
            );

            return result != null && result == 1L;
        } catch (RedisConnectionFailureException ex) {
            log.warn("Redis unavailable for rate limit check on key={}, failing open", key, ex);
            return true;
        } catch (DataAccessException ex) {
            log.warn("Redis error during rate limit check on key={}, failing open", key, ex);
            return true;
        }
    }
}

