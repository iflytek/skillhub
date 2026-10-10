package com.iflytek.skillhub.auth.federation.core;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable, adapter-supplied classification for an external subject identifier. */
public record SubjectType(String value) {

    private static final Pattern VALUE_PATTERN = Pattern.compile("[a-z][a-z0-9._-]{0,63}");

    public SubjectType {
        Objects.requireNonNull(value, "subject type must not be null");
        value = value.trim();
        if (!VALUE_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("subject type must be a normalized stable key");
        }
    }
}
