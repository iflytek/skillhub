package com.iflytek.skillhub.auth.federation.core;

import java.util.Objects;

/** Current value and authority metadata for one profile field. */
public record ProfileFieldState(
        ProfileFieldCoordinate field,
        AttributeValue value,
        ProfileFieldAuthority authority
) {
    public ProfileFieldState {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(authority, "authority");
        if (!field.scope().equals(authority.source().scope())) {
            throw new IllegalArgumentException("profile authority source scope must match field scope");
        }
    }
}
