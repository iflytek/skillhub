package com.iflytek.skillhub.auth.token;

import org.springframework.security.access.AccessDeniedException;

/**
 * Marks an API-token authorization failure whose structured reason is safe to expose to clients.
 */
public final class ApiTokenAccessDeniedException extends AccessDeniedException {

    private final String messageCode;
    private final Object[] messageArgs;

    private ApiTokenAccessDeniedException(String logMessage, String messageCode, Object... messageArgs) {
        super(logMessage);
        this.messageCode = messageCode;
        this.messageArgs = messageArgs.clone();
    }

    static ApiTokenAccessDeniedException missingScope(String requiredScope) {
        return new ApiTokenAccessDeniedException(
                "Missing API token scope: " + requiredScope,
                "error.apiToken.scope.missing",
                requiredScope
        );
    }

    static ApiTokenAccessDeniedException unsupportedEndpoint(String path) {
        return new ApiTokenAccessDeniedException(
                "API token cannot access endpoint: " + path,
                "error.apiToken.endpoint.unsupported",
                path
        );
    }

    public String getMessageCode() {
        return messageCode;
    }

    public Object[] getMessageArgs() {
        return messageArgs.clone();
    }
}
