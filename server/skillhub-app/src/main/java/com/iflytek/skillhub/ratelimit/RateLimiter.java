package com.iflytek.skillhub.ratelimit;

/**
 * Contract for key-based rate limiter implementations used by API interceptors.
 */
public interface RateLimiter {

    boolean tryAcquire(String key, int limit, int windowSeconds);
}
