package com.iflytek.skillhub.compat.dto;

public record ClawHubPublishResponse(
    boolean ok,
    String skillId,
    String versionId
) {
    public ClawHubPublishResponse(String skillId, String versionId) {
        this(true, skillId, versionId);
    }
}
