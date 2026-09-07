package com.iflytek.skillhub.auth.connection.oidc;

/** Bounded remote OIDC JSON document types. */
public enum OidcDocumentKind {
    METADATA(64 * 1024),
    JWKS(512 * 1024);

    private final int maxBytes;

    OidcDocumentKind(int maxBytes) {
        this.maxBytes = maxBytes;
    }

    int maxBytes() {
        return maxBytes;
    }
}
