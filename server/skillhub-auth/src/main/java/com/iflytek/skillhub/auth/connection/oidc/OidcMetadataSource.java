package com.iflytek.skillhub.auth.connection.oidc;

/** Remote metadata source kept outside database transactions by its caller. */
public interface OidcMetadataSource {

    OidcMetadataBundle load(OidcIssuer issuer);

    OidcJwkSetSnapshot refreshKeys(OidcProviderMetadata metadata);
}
