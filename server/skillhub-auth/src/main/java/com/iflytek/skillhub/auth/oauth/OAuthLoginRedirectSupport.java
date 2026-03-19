package com.iflytek.skillhub.auth.oauth;

/**
 * Utility methods and constants for safely handling post-login redirect targets in OAuth flows.
 */
public final class OAuthLoginRedirectSupport {

    public static final String SESSION_RETURN_TO_ATTRIBUTE = "skillhub.oauth.returnTo";
    public static final String DEFAULT_TARGET_URL = "/dashboard";

    private OAuthLoginRedirectSupport() {
    }

    public static String sanitizeReturnTo(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        String trimmed = candidate.trim();
        if (!trimmed.startsWith("/") || trimmed.startsWith("//")) {
            return null;
        }
        if (trimmed.contains("\r") || trimmed.contains("\n")) {
            return null;
        }
        return trimmed;
    }
}
