package com.iflytek.skillhub.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Validates browser navigation targets without fetching or resolving any external resource. */
final class SitePresentationLinks {
    private SitePresentationLinks() {}

    static String safeUrl(String raw) {
        if (raw == null || raw.length() > 2048) return null;
        String value = raw.strip();
        if (value.isEmpty()) return null;
        if (unsafeCharacters(raw) || value.startsWith("/") && hasDotSegment(value)) return null;
        // Decode repeatedly only for rejection checks, never for the returned navigation target.
        // This also rejects double-encoded controls and leading slashes/backslashes.
        String decoded = value;
        try {
            while (decoded.indexOf('%') >= 0) {
                String next;
                try {
                    next = URLDecoder.decode(decoded.replace("+", "%2B"), StandardCharsets.UTF_8);
                } catch (IllegalArgumentException literalPercentAfterDecoding) {
                    break;
                }
                if (next.codePoints().anyMatch(character -> character == '\\' || Character.isISOControl(character))
                        || (value.startsWith("/") && (next.startsWith("//") || hasDotSegment(next)))) return null;
                if (next.equals(decoded)) break;
                decoded = next;
            }
            URI uri = new URI(value);
            if (value.startsWith("/")) {
                return !value.startsWith("//") && uri.getScheme() == null && uri.getRawAuthority() == null
                        ? value : null;
            }
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.toLowerCase(Locale.ROOT).equals("http")
                    || scheme.toLowerCase(Locale.ROOT).equals("https"))) return null;
            if (uri.getHost() == null || uri.getHost().isBlank() || uri.getRawUserInfo() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535) return null;
            return value;
        } catch (URISyntaxException | IllegalArgumentException invalid) {
            return null;
        }
    }

    private static boolean hasDotSegment(String value) {
        int end = value.length();
        int query = value.indexOf('?');
        int fragment = value.indexOf('#');
        if (query >= 0) end = Math.min(end, query);
        if (fragment >= 0) end = Math.min(end, fragment);
        for (String segment : value.substring(0, end).split("/", -1)) {
            if (segment.equals(".") || segment.equals("..")) return true;
        }
        return false;
    }

    private static boolean unsafeCharacters(String value) {
        return value.codePoints().anyMatch(character -> character == '\\'
                || Character.isISOControl(character) || Character.isWhitespace(character));
    }
}
