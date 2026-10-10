package com.iflytek.skillhub.dto;

import java.util.List;

public record NotificationPreferenceUpdateRequest(
    List<PreferenceItem> preferences
) {
    public record PreferenceItem(String category, String channel, boolean enabled) {}
}
