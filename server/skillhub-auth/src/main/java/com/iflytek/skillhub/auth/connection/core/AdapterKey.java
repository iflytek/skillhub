package com.iflytek.skillhub.auth.connection.core;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable registry key for an authentication adapter implementation. */
public record AdapterKey(String value) {

    private static final Pattern VALUE_PATTERN = Pattern.compile("[a-z][a-z0-9._-]{0,63}");

    public AdapterKey {
        Objects.requireNonNull(value, "adapter key must not be null");
        value = value.trim();
        if (!VALUE_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("adapter key must be a normalized stable key");
        }
    }
}
