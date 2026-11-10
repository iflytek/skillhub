package com.iflytek.skillhub.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates browser navigation targets without fetching or resolving any external resource. */
final class SitePresentationLinks {
    private static final Pattern ENCODED_BYTES = Pattern.compile("(?:%[0-9a-fA-F]{2})+");

    private SitePresentationLinks() {}

    static String safeUrl(String raw) {
        if (raw == null || raw.length() > 2048) return null;
        String value = raw.strip();
        if (value.isEmpty()) return null;
        if (unsafeCharacters(raw) || value.startsWith("/") && unsafeRootPath(value)) return null;
        // Decode repeatedly only for rejection checks, never for the returned navigation target.
        // This also rejects double-encoded controls and leading slashes/backslashes.
        String decoded = value;
        try {
            while (decoded.indexOf('%') >= 0) {
                String next = decodeForChecks(decoded);
                if (next.codePoints().anyMatch(character -> character == '\\' || Character.isISOControl(character))
                        || (value.startsWith("/") && next.startsWith("//"))) return null;
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

    private static boolean unsafeRootPath(String value) {
        // Delimit the original path once. An encoded ? or # is path data, not a
        // delimiter after decoding, and must not conceal following dot segments.
        int end = value.length();
        int query = value.indexOf('?');
        int fragment = value.indexOf('#');
        if (query >= 0) end = Math.min(end, query);
        if (fragment >= 0) end = Math.min(end, fragment);
        String path = value.substring(0, end);
        while (true) {
            if (path.startsWith("//")) return true;
            for (String segment : path.split("/", -1)) {
                if (segment.equals(".") || segment.equals("..")) return true;
            }
            String decoded = decodeForChecks(path);
            if (decoded.equals(path)) return false;
            path = decoded;
        }
    }

    private static String decodeForChecks(String value) {
        // Decode valid byte sequences while retaining literal percent signs that
        // appear after a prior pass, so unrelated %25 cannot hide nested escapes.
        return ENCODED_BYTES.matcher(value).replaceAll(match -> Matcher.quoteReplacement(
                URLDecoder.decode(match.group(), StandardCharsets.UTF_8)));
    }

    private static boolean unsafeCharacters(String value) {
        return value.codePoints().anyMatch(character -> character == '\\'
                || Character.isISOControl(character) || Character.isWhitespace(character));
    }
}
