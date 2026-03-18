package com.iflytek.skillhub.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {

    private static final Pattern FORWARDED_FOR_PATTERN = Pattern.compile("for=\"?\\[?([^;,\"]+)\\]?\"?");

    public String resolve(HttpServletRequest request) {
        String forwarded = trimToNull(request.getHeader("Forwarded"));
        if (forwarded != null) {
            Matcher matcher = FORWARDED_FOR_PATTERN.matcher(forwarded);
            if (matcher.find()) {
                return normalizeCandidate(matcher.group(1));
            }
        }

        String xForwardedFor = trimToNull(request.getHeader("X-Forwarded-For"));
        if (xForwardedFor != null) {
            return normalizeCandidate(xForwardedFor.split(",")[0]);
        }

        String xRealIp = trimToNull(request.getHeader("X-Real-IP"));
        if (xRealIp != null) {
            return normalizeCandidate(xRealIp);
        }

        return normalizeCandidate(request.getRemoteAddr());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || "unknown".equalsIgnoreCase(trimmed) ? null : trimmed;
    }

    private String normalizeCandidate(String candidate) {
        String normalized = trimToNull(candidate);
        if (normalized == null) {
            return "unknown";
        }
        int zoneIndex = normalized.indexOf('%');
        if (zoneIndex >= 0) {
            normalized = normalized.substring(0, zoneIndex);
        }
        return normalized;
    }
}
