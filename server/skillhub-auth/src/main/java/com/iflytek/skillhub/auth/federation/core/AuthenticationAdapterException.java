package com.iflytek.skillhub.auth.federation.core;

import java.util.Objects;

/** Authentication Adapter failure whose public shape cannot embed provider payloads or Secrets. */
public final class AuthenticationAdapterException extends RuntimeException {

    private final AuthenticationAdapterFailureReason reason;

    public AuthenticationAdapterException(AuthenticationAdapterFailureReason reason) {
        super(Objects.requireNonNull(reason, "reason").publicMessage());
        this.reason = reason;
    }

    public AuthenticationAdapterFailureReason reason() {
        return reason;
    }

    public boolean retryable() {
        return reason.retryable();
    }
}
