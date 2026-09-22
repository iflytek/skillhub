package com.iflytek.skillhub.auth.connection.oidc;

/** Enumeration-resistant failure for untrusted OIDC metadata and key retrieval. */
public final class OidcMetadataUnavailableException extends RuntimeException {

    public OidcMetadataUnavailableException() {
        super("OIDC provider metadata is unavailable", null, false, false);
    }
}
