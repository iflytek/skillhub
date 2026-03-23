package com.iflytek.skillhub.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.iflytek.skillhub.notification.sse.SseEmitterManager;
import com.iflytek.skillhub.ratelimit.RateLimitInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;

class WebMvcRateLimitConfigTest {

    @Test
    void configureAsyncSupport_shouldSetTimeoutToMatchSseTimeout() {
        WebMvcRateLimitConfig config = new WebMvcRateLimitConfig(mock(RateLimitInterceptor.class));
        TestAsyncSupportConfigurer asyncSupportConfigurer = new TestAsyncSupportConfigurer();

        config.configureAsyncSupport(asyncSupportConfigurer);

        assertThat(asyncSupportConfigurer.timeout()).isEqualTo(SseEmitterManager.defaultTimeoutMillis());
    }

    private static final class TestAsyncSupportConfigurer extends AsyncSupportConfigurer {
        private Long timeout() {
            return getTimeout();
        }
    }
}
