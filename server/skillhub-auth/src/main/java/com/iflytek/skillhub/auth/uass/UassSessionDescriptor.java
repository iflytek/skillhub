package com.iflytek.skillhub.auth.uass;

import java.time.Instant;
import java.util.Map;

/**
 * Provider-facing session snapshot used for remote status checks and logout.
 */
public record UassSessionDescriptor(
        String userCode,
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt,
        Map<String, String> attributes
) {

    public UassSessionDescriptor {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
