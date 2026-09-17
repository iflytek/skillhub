package com.iflytek.skillhub.auth.federation.core;

import java.util.Objects;

/** Proposed profile value whose authority must be resolved from platform policy. */
public record ProfileFieldCandidate(
        ProfileFieldCoordinate field,
        AttributeValue value,
        ProfileValueSource source
) {
    public ProfileFieldCandidate {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(source, "source");
        if (!field.scope().equals(source.scope())) {
            throw new IllegalArgumentException("profile source scope must match field scope");
        }
    }
}
