package com.iflytek.skillhub.auth.federation.core;

/** Stable, privacy-safe categories returned by authentication adapters. */
public enum AuthenticationAdapterFailureReason {
    INVALID_ASSERTION("The identity provider response is invalid.", false),
    AUTHENTICATION_DENIED("Authentication was denied by the identity provider.", false),
    UPSTREAM_UNAVAILABLE("The identity provider is temporarily unavailable.", true),
    CONNECTION_MISCONFIGURED("The identity connection is not configured correctly.", false);

    private final String publicMessage;
    private final boolean retryable;

    AuthenticationAdapterFailureReason(String publicMessage, boolean retryable) {
        this.publicMessage = publicMessage;
        this.retryable = retryable;
    }

    public String publicMessage() {
        return publicMessage;
    }

    public boolean retryable() {
        return retryable;
    }
}
