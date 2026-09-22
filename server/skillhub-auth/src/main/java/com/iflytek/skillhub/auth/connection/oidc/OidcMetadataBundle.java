package com.iflytek.skillhub.auth.connection.oidc;

import java.util.Objects;

/** One internally consistent metadata/JWKS observation. */
public record OidcMetadataBundle(
        OidcProviderMetadata metadata,
        OidcJwkSetSnapshot jwkSet
) {

    public OidcMetadataBundle {
        metadata = Objects.requireNonNull(metadata, "metadata");
        jwkSet = Objects.requireNonNull(jwkSet, "jwkSet");
    }
}
