package com.iflytek.skillhub.auth.connection.oidc;

import java.time.Instant;

/** Narrow preflight port that keeps control-plane tests independent of the metadata cache. */
@FunctionalInterface
public interface OidcConnectionMetadataProbe {

    void verify(OidcMetadataCacheKey key, OidcIssuer issuer, Instant occurredAt);
}
