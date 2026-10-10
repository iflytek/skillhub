package com.iflytek.skillhub.compat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ClawHubRegistryOwner(
        String handle,
        String displayName,
        String image
) {
    public static ClawHubRegistryOwner empty() {
        return new ClawHubRegistryOwner(null, null, null);
    }
}
