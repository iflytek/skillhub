package com.iflytek.skillhub.notification.email;

public record NotificationEmailPolicy(boolean enabled, int digestWindowMinutes, String publicBaseUrl, long version) {}
