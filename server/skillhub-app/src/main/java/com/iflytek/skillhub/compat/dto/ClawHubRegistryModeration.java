package com.iflytek.skillhub.compat.dto;

import java.util.List;

public record ClawHubRegistryModeration(
        boolean isSuspicious,
        boolean isMalwareBlocked,
        String verdict,
        List<String> reasonCodes,
        Long updatedAt,
        String engineVersion,
        String summary
) {
    public static ClawHubRegistryModeration clean() {
        return new ClawHubRegistryModeration(
                false,
                false,
                "clean",
                List.of(),
                null,
                null,
                null
        );
    }
}
