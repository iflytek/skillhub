package com.iflytek.skillhub.auth.uass.store;

import java.time.Instant;
import java.util.Objects;
import org.springframework.util.StringUtils;

public record UassLoginState(
        String returnTo,
        Instant createdAt,
        String provider,
        String requestFingerprint
) {

    public UassLoginState {
        if (!StringUtils.hasText(returnTo)) {
            throw new IllegalArgumentException("returnTo must not be blank");
        }
        returnTo = returnTo.trim();
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        provider = StringUtils.hasText(provider) ? provider.trim() : "uass";
        requestFingerprint = StringUtils.hasText(requestFingerprint) ? requestFingerprint.trim() : null;
    }
}
