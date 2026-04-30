package com.iflytek.skillhub.auth.uass;

import java.time.Instant;
import java.util.Map;

/**
 * Normalized login validation result returned by a {@link UassGateway}.
 */
public record UassValidatedLogin(
        String userCode,
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt,
        Map<String, String> attributes
) {

    public UassValidatedLogin {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
