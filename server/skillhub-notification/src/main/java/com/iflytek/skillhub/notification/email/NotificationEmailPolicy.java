package com.iflytek.skillhub.notification.email;

import java.time.Instant;

public record NotificationEmailPolicy(boolean enabled, int digestWindowMinutes, String publicBaseUrl,
        long version, Instant enabledSince) {
    public NotificationEmailPolicy(boolean enabled, int digestWindowMinutes, String publicBaseUrl, long version) {
        this(enabled, digestWindowMinutes, publicBaseUrl, version, null);
    }
}
