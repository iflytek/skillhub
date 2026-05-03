package com.iflytek.skillhub.config;

import java.util.List;
import java.util.Map;
import org.springframework.core.env.Environment;

final class RuntimeStatePropertyDefaults {

    static final String PROVIDER_PROPERTY = "skillhub.runtime.state.provider";
    static final String PROPERTY_SOURCE_NAME = "skillhubRuntimeStateDefaults";

    private static final List<String> MEMORY_AUTOCONFIG_EXCLUDES = List.of(
            "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
    );

    private RuntimeStatePropertyDefaults() {
    }

    static Map<String, Object> resolveOverrides(Environment environment) {
        RuntimeStateProvider provider = RuntimeStateProvider.fromProperty(environment.getProperty(PROVIDER_PROPERTY));
        if (provider == null) {
            return Map.of();
        }
        return switch (provider) {
            case MEMORY -> Map.of(
                    "spring.session.store-type", "none",
                    "spring.autoconfigure.exclude", MEMORY_AUTOCONFIG_EXCLUDES,
                    "skillhub.ratelimit.mode", "memory",
                    "skillhub.auth.failure-throttle.mode", "memory",
                    "skillhub.auth.uass.cache-mode", "local"
            );
            case REDIS -> Map.of(
                    "spring.session.store-type", "redis",
                    "spring.autoconfigure.exclude", List.of(),
                    "skillhub.ratelimit.mode", "redis",
                    "skillhub.auth.failure-throttle.mode", "redis",
                    "skillhub.auth.uass.cache-mode", "redis"
            );
        };
    }

    static List<String> memoryAutoConfigurationExcludes() {
        return MEMORY_AUTOCONFIG_EXCLUDES;
    }
}
