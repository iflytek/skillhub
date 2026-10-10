package com.iflytek.skillhub.ratelimit;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Test-profile rate limiter that keeps sliding-window counters in memory.
 */
@Component
@Profile("test")
public class InMemorySlidingWindowRateLimiter implements RateLimiter {

    private final ConcurrentHashMap<String, Deque<Long>> requests = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, int limit, int windowSeconds) {
        long now = System.currentTimeMillis();
        long windowMillis = windowSeconds * 1000L;
        Deque<Long> timestamps = requests.computeIfAbsent(key, ignored -> new ConcurrentLinkedDeque<>());

        synchronized (timestamps) {
            evictExpired(timestamps, now - windowMillis);
            if (timestamps.size() >= limit) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    private void evictExpired(Deque<Long> timestamps, long threshold) {
        while (!timestamps.isEmpty()) {
            Long oldest = timestamps.peekFirst();
            if (oldest == null || oldest >= threshold) {
                return;
            }
            timestamps.pollFirst();
        }
    }
}
