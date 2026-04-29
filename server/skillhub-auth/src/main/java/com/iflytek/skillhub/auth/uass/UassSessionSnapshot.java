package com.iflytek.skillhub.auth.uass;

import java.io.Serial;
import java.io.Serializable;
import java.net.URI;
import java.time.Instant;
import java.util.Map;

/**
 * Serializable UASS session snapshot kept alongside the platform session so
 * status/logout flows can optionally call back into the upstream provider.
 */
public record UassSessionSnapshot(
        String state,
        URI callbackUri,
        String userCode,
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt,
        Map<String, String> attributes
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public UassSessionSnapshot {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static UassSessionSnapshot from(UassLoginContext loginContext) {
        return new UassSessionSnapshot(
                loginContext.state(),
                loginContext.callbackUri(),
                loginContext.userCode(),
                loginContext.accessToken(),
                loginContext.refreshToken(),
                loginContext.accessTokenExpiresAt(),
                loginContext.attributes()
        );
    }

    public UassLoginContext toLoginContext() {
        return new UassLoginContext(
                state,
                callbackUri,
                userCode,
                accessToken,
                refreshToken,
                accessTokenExpiresAt,
                attributes
        );
    }
}
