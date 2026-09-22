package com.iflytek.skillhub.auth.connection.core;

import java.util.Objects;

/** Deterministic startup or lookup failure for the built-in adapter registry. */
public final class AdapterRegistryException extends IllegalStateException {

    private final AdapterRegistryFailureReason reason;

    public AdapterRegistryException(AdapterRegistryFailureReason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public AdapterRegistryFailureReason reason() {
        return reason;
    }
}
