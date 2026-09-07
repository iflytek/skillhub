package com.iflytek.skillhub.auth.connection.core;

/** Privacy-preserving failure for a missing, inactive or unusable login connection. */
public final class ConnectionUnavailableException extends RuntimeException {

    public ConnectionUnavailableException() {
        super("Login connection is unavailable");
    }
}
