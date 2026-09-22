package com.iflytek.skillhub.auth.connection.oidc;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

/** Validated provider metadata used by later authorization and token validation stages. */
public record OidcProviderMetadata(
        OidcIssuer issuer,
        URI authorizationEndpoint,
        URI tokenEndpoint,
        URI jwksUri,
        Set<String> idTokenSigningAlgorithms
) {

    public OidcProviderMetadata {
        issuer = Objects.requireNonNull(issuer, "issuer");
        authorizationEndpoint = Objects.requireNonNull(
                authorizationEndpoint,
                "authorizationEndpoint"
        );
        tokenEndpoint = Objects.requireNonNull(tokenEndpoint, "tokenEndpoint");
        jwksUri = Objects.requireNonNull(jwksUri, "jwksUri");
        idTokenSigningAlgorithms = Set.copyOf(Objects.requireNonNull(
                idTokenSigningAlgorithms,
                "idTokenSigningAlgorithms"
        ));
    }
}
