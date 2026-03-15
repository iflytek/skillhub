package com.iflytek.skillhub.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SensitiveLogSanitizer {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwd", "pwd", "token", "authorization", "cookie",
            "secret", "api_key", "apikey", "access_key", "refresh_token", "code");

    public String sanitizeRequestTarget(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        if (!StringUtils.hasText(query)) {
            return uri;
        }
        return uri + "?" + sanitizeQuery(query);
    }

    String sanitizeQuery(String query) {
        return Arrays.stream(query.split("&"))
                .map(this::sanitizeQueryPart)
                .collect(Collectors.joining("&"));
    }

    private String sanitizeQueryPart(String queryPart) {
        int idx = queryPart.indexOf('=');
        if (idx < 0) {
            return queryPart;
        }
        String key = queryPart.substring(0, idx);
        String normalizedKey = key.trim().toLowerCase(Locale.ROOT);
        if (SENSITIVE_KEYS.contains(normalizedKey)) {
            return key + "=[REDACTED]";
        }
        return queryPart;
    }
}
