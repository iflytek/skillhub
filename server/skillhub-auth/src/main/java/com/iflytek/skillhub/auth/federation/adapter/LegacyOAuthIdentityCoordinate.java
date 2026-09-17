package com.iflytek.skillhub.auth.federation.adapter;

import com.iflytek.skillhub.auth.federation.core.SubjectType;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/** Stable V2 coordinate components for one legacy platform OAuth provider. */
public final class LegacyOAuthIdentityCoordinate {

    public static final URI ISSUER = URI.create("urn:skillhub:legacy-oauth");
    public static final SubjectType SUBJECT_TYPE = new SubjectType("legacy-oauth-subject");

    private static final Pattern PROVIDER_PATTERN = Pattern.compile("[a-z][a-z0-9._-]{0,63}");
    private static final String CONNECTION_ID_PREFIX = "legacy-oauth-";
    private static final String CONNECTION_ID_NAMESPACE = "skillhub:legacy-oauth:";

    private LegacyOAuthIdentityCoordinate() {
    }

    public static String connectionId(String provider) {
        String normalized = requireProvider(provider);
        return CONNECTION_ID_PREFIX + md5(CONNECTION_ID_NAMESPACE + normalized);
    }

    public static String requireProvider(String provider) {
        Objects.requireNonNull(provider, "provider");
        String normalized = provider.trim();
        if (!PROVIDER_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("provider must be a normalized stable key");
        }
        return normalized;
    }

    private static String md5(String value) {
        try {
            // MD5 is used only for a deterministic opaque database identifier, not for security.
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM does not provide required MD5 identifier digest", impossible);
        }
    }
}
