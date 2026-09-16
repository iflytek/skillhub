package com.iflytek.skillhub.dto;

public record GovernanceInboxItemResponse(
        String type,
        Long id,
        String title,
        String subtitle,
        String timestamp,
        String namespace,
        String skillSlug,
        String resourceType,
        String resourceSlug
) {
    /** Backward-compatible constructor for existing Skill-centric inbox items. */
    public GovernanceInboxItemResponse(
            String type,
            Long id,
            String title,
            String subtitle,
            String timestamp,
            String namespace,
            String skillSlug
    ) {
        this(type, id, title, subtitle, timestamp, namespace, skillSlug,
                skillSlug == null ? null : "SKILL", skillSlug);
    }
}
