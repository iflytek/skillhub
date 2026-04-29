package com.iflytek.skillhub.auth.uass;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

/**
 * Normalized UASS login result carried across application services without
 * leaking any upstream jar-specific response types.
 */
public record UassLoginContext(
        String state,
        URI callbackUri,
        String userCode,
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt,
        Map<String, String> attributes
) {

    public UassLoginContext {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
