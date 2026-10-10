package com.iflytek.skillhub.auth.federation.core;

import java.util.Objects;
import java.util.Optional;

/** Candidate Platform Account and/or pre-provisioned Organization Membership. */
public record IdentityCorrelationTarget(Optional<String> userId, Optional<String> membershipId) {

    public IdentityCorrelationTarget {
        userId = normalizeOptionalText(userId, "userId");
        membershipId = normalizeOptionalText(membershipId, "membershipId");
        if (userId.isEmpty() && membershipId.isEmpty()) {
            throw new IllegalArgumentException("correlation target must identify an account or membership");
        }
    }

    public static IdentityCorrelationTarget account(String userId) {
        return new IdentityCorrelationTarget(Optional.of(userId), Optional.empty());
    }

    public static IdentityCorrelationTarget membership(String membershipId) {
        return new IdentityCorrelationTarget(Optional.empty(), Optional.of(membershipId));
    }

    public static IdentityCorrelationTarget linked(String userId, String membershipId) {
        return new IdentityCorrelationTarget(Optional.of(userId), Optional.of(membershipId));
    }

    private static Optional<String> normalizeOptionalText(Optional<String> value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        return value.map(text -> {
            String normalized = Objects.requireNonNull(text, field + " must not be null").trim();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException(field + " must not be blank");
            }
            return normalized;
        });
    }
}
