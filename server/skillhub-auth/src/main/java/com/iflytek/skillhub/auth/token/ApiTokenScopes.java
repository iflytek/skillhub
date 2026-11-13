package com.iflytek.skillhub.auth.token;

/**
 * Default self-service API token profile.
 */
public final class ApiTokenScopes {

    public static final String READ = "skill:read";
    public static final String SUBMIT = "skill:submit";
    public static final String PUBLISH_PRIVATE = "skill:publish-private";
    public static final String MANAGE = "skill:manage";
    public static final String DELETE = "skill:delete";
    public static final String TOKEN_MANAGE = "token:manage";
    public static final String LEGACY_PUBLISH = "skill:publish";
    public static final String SUITE_LEGACY_WRITE = "suite:legacy-write";

    public static final String DEFAULT_USER_SCOPE_JSON =
            "[\"skill:read\",\"skill:publish\",\"skill:delete\"]";
    public static final String CLI_SCOPE_JSON = "[\"skill:read\",\"skill:submit\"]";

    private ApiTokenScopes() {
    }
}
