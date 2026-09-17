package com.iflytek.skillhub.auth.federation.core;

import java.util.Objects;

/** A normalized profile field within exactly one global or organization scope. */
public record ProfileFieldCoordinate(ProfileScope scope, AttributeKey field) {
    public ProfileFieldCoordinate {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(field, "field");
    }
}
