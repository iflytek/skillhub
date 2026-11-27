package com.iflytek.skillhub.auth.connection.oidc;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.function.Function;

/** Checks metadata and signing keys without sending any client secret or performing a login. */
public class OidcConnectionProbe {

    public enum Reason {
        BLOCKED,
        TIMEOUT,
        UPSTREAM_UNAVAILABLE,
        INVALID_METADATA
    }

    public static final class Failure extends RuntimeException {
        private final Reason reason;

        public Failure(Reason reason) {
            super(reason.name());
            this.reason = reason;
        }

        public Reason getReason() {
            return reason;
        }
    }

    private final Function<URI, byte[]> fetch;
    private final ObjectReader json;

    OidcConnectionProbe(Function<URI, byte[]> fetch, ObjectMapper mapper) {
        this.fetch = fetch;
        this.json = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public void verify(OidcConnectionConfiguration configuration) {
        try {
            JsonNode metadata = json.readTree(fetch.apply(configuration.discoveryEndpoint()));
            if (metadata == null || !configuration.issuer().equals(metadata.path("issuer").textValue())) {
                throw new Failure(Reason.INVALID_METADATA);
            }
            OidcConnectionConfiguration.requireHttpsEndpoint(metadata.path("authorization_endpoint").textValue());
            OidcConnectionConfiguration.requireHttpsEndpoint(metadata.path("token_endpoint").textValue());
            URI jwks = OidcConnectionConfiguration.requireHttpsEndpoint(metadata.path("jwks_uri").textValue());
            JWKSet keys = JWKSet.parse(new String(fetch.apply(jwks), StandardCharsets.UTF_8));
            if (keys.getKeys().stream().noneMatch(key -> !key.isPrivate() && key.toPublicJWK() != null
                    && (key.getKeyUse() == null || KeyUse.SIGNATURE.equals(key.getKeyUse())))) {
                throw new Failure(Reason.INVALID_METADATA);
            }
        } catch (IllegalArgumentException | IOException | ParseException invalid) {
            throw new Failure(Reason.INVALID_METADATA);
        }
    }
}
