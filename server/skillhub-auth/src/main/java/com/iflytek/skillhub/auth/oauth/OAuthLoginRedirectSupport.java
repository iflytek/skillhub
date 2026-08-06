package com.iflytek.skillhub.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Utility methods and constants for safely handling OAuth redirect targets and
 * deployment-path aware browser-visible URLs.
 */
public final class OAuthLoginRedirectSupport {

    public static final String SESSION_RETURN_TO_ATTRIBUTE = "skillhub.oauth.returnTo";
    public static final String DEFAULT_TARGET_URL = "/dashboard";

    private OAuthLoginRedirectSupport() {
    }

    public static String apiBase(HttpServletRequest request) {
        return deploymentPrefix(request) + "/api/v1";
    }

    public static String webRoot(HttpServletRequest request) {
        return deploymentPrefix(request) + "/";
    }

    static String deploymentPrefix(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String rawPrefix = request.getContextPath();
        if (rawPrefix == null || rawPrefix.isBlank() || "/".equals(rawPrefix)) {
            return "";
        }
        String prefix = rawPrefix.endsWith("/") ? rawPrefix.substring(0, rawPrefix.length() - 1) : rawPrefix;
        if (!prefix.startsWith("/") || prefix.contains("//") || prefix.contains("\\")
                || prefix.contains("?") || prefix.contains("#")) {
            return "";
        }
        return prefix;
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
