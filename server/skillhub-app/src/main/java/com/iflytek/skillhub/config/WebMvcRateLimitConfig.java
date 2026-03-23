package com.iflytek.skillhub.config;

import com.iflytek.skillhub.notification.sse.SseEmitterManager;
import com.iflytek.skillhub.ratelimit.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers MVC interceptors related to request rate limiting.
 */
@Configuration
public class WebMvcRateLimitConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    public WebMvcRateLimitConfig(RateLimitInterceptor rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**");
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        // Keep MVC async timeouts above the SSE emitter timeout so EventSource
        // connections are not forcibly torn down every few seconds.
        configurer.setDefaultTimeout(SseEmitterManager.defaultTimeoutMillis());
    }
}
