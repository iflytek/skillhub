package com.iflytek.skillhub.dto;

public record UassLoginStatusResponse(
        boolean authenticated,
        String provider,
        Boolean remoteAuthenticated
) {
}
