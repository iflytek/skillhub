package com.iflytek.skillhub.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySlidingWindowRateLimiterTest {

    private final InMemorySlidingWindowRateLimiter limiter = new InMemorySlidingWindowRateLimiter();

    @Test
    void tryAcquire_withinLimit_returnsTrue() {
        assertThat(limiter.tryAcquire("key-1", 3, 60)).isTrue();
        assertThat(limiter.tryAcquire("key-1", 3, 60)).isTrue();
        assertThat(limiter.tryAcquire("key-1", 3, 60)).isTrue();
    }

    @Test
    void tryAcquire_exceedsLimit_returnsFalse() {
        assertThat(limiter.tryAcquire("key-2", 2, 60)).isTrue();
        assertThat(limiter.tryAcquire("key-2", 2, 60)).isTrue();
        assertThat(limiter.tryAcquire("key-2", 2, 60)).isFalse();
    }

    @Test
    void tryAcquire_afterWindowExpires_returnsTrue() throws InterruptedException {
        assertThat(limiter.tryAcquire("key-3", 1, 1)).isTrue();
        Thread.sleep(1100);
        assertThat(limiter.tryAcquire("key-3", 1, 1)).isTrue();
    }
}
