package com.iflytek.skillhub.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Runtime-configurable overrides for request rate limiting.
 *
 * <p>The compile-time {@link com.iflytek.skillhub.ratelimit.RateLimit} annotation on each endpoint
 * supplies the built-in defaults. Values set here — typically via {@code SKILLHUB_RATELIMIT_*}
 * environment variables — override those defaults per {@code category}, and {@code enabled=false}
 * turns request rate limiting off entirely. When nothing is configured the annotation defaults are
 * used unchanged, so existing deployments behave exactly as before.
 *
 * <p>An override applies to every endpoint that shares the same {@code category}. Only the fields
 * you set are overridden; the rest fall back to the annotation.
 */
@Component
@ConfigurationProperties(prefix = "skillhub.ratelimit")
public class RateLimitProperties {

    /** Master switch. When {@code false} the interceptor performs no quota checks. */
    private boolean enabled = true;

    /** Per-category overrides keyed by {@code RateLimit#category} (e.g. "search", "download", "publish"). */
    private Map<String, CategoryLimit> categories = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, CategoryLimit> getCategories() {
        return categories;
    }

    public void setCategories(Map<String, CategoryLimit> categories) {
        this.categories = categories;
    }

    /** Configured authenticated quota for {@code category}, or {@code fallback} when unset. */
    public int authenticatedFor(String category, int fallback) {
        CategoryLimit c = categories.get(category);
        return c != null && c.getAuthenticated() != null ? c.getAuthenticated() : fallback;
    }

    /** Configured anonymous quota for {@code category}, or {@code fallback} when unset. */
    public int anonymousFor(String category, int fallback) {
        CategoryLimit c = categories.get(category);
        return c != null && c.getAnonymous() != null ? c.getAnonymous() : fallback;
    }

    /** Configured window (seconds) for {@code category}, or {@code fallback} when unset. */
    public int windowSecondsFor(String category, int fallback) {
        CategoryLimit c = categories.get(category);
        return c != null && c.getWindowSeconds() != null ? c.getWindowSeconds() : fallback;
    }

    public static class CategoryLimit {
        private Integer authenticated;
        private Integer anonymous;
        private Integer windowSeconds;

        public Integer getAuthenticated() {
            return authenticated;
        }

        public void setAuthenticated(Integer authenticated) {
            this.authenticated = authenticated;
        }

        public Integer getAnonymous() {
            return anonymous;
        }

        public void setAnonymous(Integer anonymous) {
            this.anonymous = anonymous;
        }

        public Integer getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(Integer windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }
}
