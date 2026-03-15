package com.iflytek.skillhub.compat.dto;

public record ClawHubWhoamiResponse(
    User user
) {
    public ClawHubWhoamiResponse(String handle, String displayName, String image) {
        this(new User(handle, displayName, image));
    }

    public record User(
        String handle,
        String displayName,
        String image
    ) {}
}
