package com.iflytek.skillhub.auth.connection.control;

import java.util.Objects;

/** Safe Adapter boundary failure: the original upstream exception is intentionally not exposed. */
public final class LoginConnectionTestException extends RuntimeException {

    private final LoginConnectionTestFailureReason reason;

    public LoginConnectionTestException(LoginConnectionTestFailureReason reason) {
        super("Login connection test failed: " + Objects.requireNonNull(reason, "reason"));
        this.reason = reason;
    }

    public LoginConnectionTestFailureReason reason() {
        return reason;
    }
}
