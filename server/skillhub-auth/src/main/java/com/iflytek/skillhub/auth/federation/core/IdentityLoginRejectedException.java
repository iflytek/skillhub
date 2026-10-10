package com.iflytek.skillhub.auth.federation.core;

import java.util.Objects;

/** Internal denial carrying an auditable reason without exposing account state to callers. */
public final class IdentityLoginRejectedException extends RuntimeException {

    private final IdentityLoginRejectionReason reason;

    public IdentityLoginRejectedException(IdentityLoginRejectionReason reason) {
        super("Enterprise identity login was rejected");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public IdentityLoginRejectionReason reason() {
        return reason;
    }
}
