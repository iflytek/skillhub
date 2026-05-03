package com.iflytek.skillhub.config;

import java.util.Locale;
import org.springframework.util.StringUtils;

enum RuntimeStateProvider {
    MEMORY,
    REDIS;

    static RuntimeStateProvider fromProperty(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "memory" -> MEMORY;
            case "redis" -> REDIS;
            default -> throw new IllegalStateException(
                    "Unsupported skillhub.runtime.state.provider: " + value + " (expected memory or redis)"
            );
        };
    }
}
