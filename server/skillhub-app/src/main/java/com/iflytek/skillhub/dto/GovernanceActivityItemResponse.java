package com.iflytek.skillhub.dto;

public record GovernanceActivityItemResponse(
        Long id,
        String action,
        String actorUserId,
        String actorDisplayName,
        String targetType,
        String targetId,
        String details,
        String timestamp
) {
}
