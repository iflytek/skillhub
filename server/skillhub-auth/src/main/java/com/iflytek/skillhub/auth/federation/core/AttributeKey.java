package com.iflytek.skillhub.auth.federation.core;

import java.util.Objects;
import java.util.regex.Pattern;

/** Namespaced platform key for an adapter-normalized identity attribute. */
public record AttributeKey(String value) {

    private static final Pattern VALUE_PATTERN = Pattern.compile("[a-z][a-z0-9_-]*(?:\\.[a-z][a-z0-9_-]*)+");

    public AttributeKey {
        Objects.requireNonNull(value, "attribute key must not be null");
        value = value.trim();
        if (!VALUE_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("attribute key must be a normalized namespaced key");
        }
    }
}
