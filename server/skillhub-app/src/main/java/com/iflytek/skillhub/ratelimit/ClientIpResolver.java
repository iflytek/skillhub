package com.iflytek.skillhub.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Resolves the best-effort client IP address from proxy-aware request headers.
 * Only trusts proxy headers (X-Forwarded-For, Forwarded, X-Real-IP) when the immediate
 * client is in the trusted-proxies list. Otherwise falls back to remoteAddr.
 */
@Component
public class ClientIpResolver {

    private static final Pattern FORWARDED_FOR_PATTERN = Pattern.compile("for=\"?\\[?([^;,\"]+)\\]?\"?");
    private final List<String> trustedProxies;

    public ClientIpResolver(RateLimitProperties properties) {
        this.trustedProxies = properties.getTrustedProxies();
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        if (!isTrustedProxy(remoteAddr)) {
            return normalizeCandidate(remoteAddr);
        }

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

        return normalizeCandidate(remoteAddr);
    }

    private boolean isTrustedProxy(String ip) {
        if (trustedProxies.isEmpty()) {
            return false;
        }
        return trustedProxies.contains(ip);
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
