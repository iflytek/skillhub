package com.iflytek.skillhub.dto;
public record NotificationEmailSettingsUpdateRequest(
        boolean enabled, int digestWindowMinutes, String publicBaseUrl, long version) {}
