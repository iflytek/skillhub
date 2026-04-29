package com.iflytek.skillhub.auth.uass;

import java.util.Map;

/**
 * Internal profile snapshot returned from UASS after the facade maps away
 * vendor-specific response objects.
 */
public record UassUserProfile(
        String userCode,
        String displayName,
        String email,
        String mobile,
        String employeeNumber,
        Map<String, String> attributes
) {

    public UassUserProfile {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
