package com.iflytek.skillhub.auth.connection.oidc;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import java.net.URI;
import java.util.regex.Pattern;

/** Non-secret configuration stored on the immutable connection revision. */
public record OidcConnectionConfiguration(String issuer, String clientId) {

    private static final Pattern DNS_NAME = Pattern.compile(
            "(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+"
                    + "[A-Za-z](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?");

    public OidcConnectionConfiguration {
        issuer = issuer == null ? "" : issuer.strip();
        clientId = clientId == null ? "" : clientId.strip();
        try {
            URI uri = requireHttpsEndpoint(issuer);
            if (issuer.length() > 512 || uri.getRawQuery() != null || !uri.normalize().equals(uri)
                    || clientId.isBlank() || clientId.length() > 512
                    || clientId.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException badConfiguration) {
            throw new DomainBadRequestException("error.loginConnection.config.invalid");
        }
    }

    URI discoveryEndpoint() {
        String prefix = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
        return URI.create(prefix + "/.well-known/openid-configuration");
    }

    static URI requireHttpsEndpoint(String value) {
        if (value == null || value.isBlank() || value.length() > 2048) {
            throw new IllegalArgumentException();
        }
        URI uri = URI.create(value);
        String host = uri.getHost();
        if (!"https".equals(uri.getScheme()) || host == null || host.length() > 253
                || !DNS_NAME.matcher(host).matches() || uri.getRawUserInfo() != null
                || uri.getRawFragment() != null || uri.getPort() < -1 || uri.getPort() == 0
                || uri.getPort() > 65535) {
            throw new IllegalArgumentException();
        }
        return uri;
    }
}
