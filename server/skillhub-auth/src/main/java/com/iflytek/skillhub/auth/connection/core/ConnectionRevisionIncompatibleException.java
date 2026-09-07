package com.iflytek.skillhub.auth.connection.core;

import java.util.Objects;

/** Fail-closed control-plane error that leaves the current active pointer unchanged. */
public final class ConnectionRevisionIncompatibleException extends IllegalStateException {

    private final ConnectionRevisionIncompatibility reason;

    public ConnectionRevisionIncompatibleException(
            ConnectionRevisionIncompatibility reason,
            String message
    ) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public ConnectionRevisionIncompatibility reason() {
        return reason;
    }
}
