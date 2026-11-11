package com.iflytek.skillhub.dto;
public record NotificationEmailSettingsResponse(
        boolean enabled, int digestWindowMinutes, String publicBaseUrl, long version, boolean smtpConfigured) {}
