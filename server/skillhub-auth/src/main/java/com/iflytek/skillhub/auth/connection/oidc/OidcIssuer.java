package com.iflytek.skillhub.auth.connection.oidc;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;

/** Canonical configured OIDC issuer whose value is used for exact metadata/token matching. */
public record OidcIssuer(String value, URI uri) {

    public OidcIssuer(String value) {
        this(requireCanonicalValue(value), parse(value));
    }

    public OidcIssuer {
        value = requireCanonicalValue(value);
        uri = Objects.requireNonNull(uri, "uri");
        if (!value.equals(uri.toASCIIString())
                || !"https".equals(uri.getScheme())
                || uri.getHost() == null
                || uri.getHost().isBlank()
                || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("OIDC issuer must be a canonical HTTPS URL");
        }
        if (!uri.getHost().equals(uri.getHost().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("OIDC issuer host must be lowercase");
        }
    }

    public URI discoveryUri() {
        String prefix = value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
        return URI.create(prefix + "/.well-known/openid-configuration");
    }

    private static URI parse(String value) {
        try {
            return new URI(requireCanonicalValue(value));
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("OIDC issuer must be a canonical HTTPS URL");
        }
    }

    private static String requireCanonicalValue(String value) {
        Objects.requireNonNull(value, "value");
        if (value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException("OIDC issuer must be a canonical HTTPS URL");
        }
        return value;
    }
}
