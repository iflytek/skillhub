package com.iflytek.skillhub.compat.dto;

public record ClawHubDeleteResponse(
    boolean ok
) {
    public ClawHubDeleteResponse() {
        this(true);
    }
}
