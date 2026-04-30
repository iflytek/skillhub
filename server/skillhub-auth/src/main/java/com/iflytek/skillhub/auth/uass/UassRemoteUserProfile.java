package com.iflytek.skillhub.auth.uass;

import java.util.Map;

/**
 * Raw user profile data loaded from the upstream UASS provider.
 */
public record UassRemoteUserProfile(
        String userCode,
        String displayName,
        String email,
        String mobile,
        String employeeNumber,
        Map<String, String> attributes
) {

    public UassRemoteUserProfile {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
