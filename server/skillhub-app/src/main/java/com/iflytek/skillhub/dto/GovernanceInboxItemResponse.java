package com.iflytek.skillhub.dto;

public record GovernanceInboxItemResponse(
        String type,
        Long id,
        String title,
        String subtitle,
        String timestamp,
        String namespace,
        String skillSlug
) {
}
