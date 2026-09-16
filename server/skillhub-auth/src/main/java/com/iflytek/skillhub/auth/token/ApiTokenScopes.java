package com.iflytek.skillhub.auth.token;

/** Default self-service API token profile. */
public final class ApiTokenScopes {

    public static final String DEFAULT_USER_SCOPE_JSON =
            "[\"skill:read\",\"skill:publish\",\"skill:delete\"]";

    private ApiTokenScopes() {
    }
}
