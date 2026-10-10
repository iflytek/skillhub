package com.iflytek.skillhub.auth.connection.core;

import java.util.Objects;
import java.util.regex.Pattern;

/** Opaque public handle used to route a login request to an active connection. */
public record ConnectionHandle(String value) {

    private static final Pattern VALUE_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{7,127}");

    public ConnectionHandle {
        Objects.requireNonNull(value, "connection handle must not be null");
        value = value.trim();
        if (!VALUE_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("connection handle must be an opaque stable identifier");
        }
    }
}
