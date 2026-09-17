package com.iflytek.skillhub.auth.federation.core;

import java.util.Objects;
import java.util.Optional;

/** Field-level authority granted to a source family or one exact connection source. */
public record ProfileAuthorityRule(
        ProfileSourceKind kind,
        Optional<String> sourceId,
        int priority
) {
    public ProfileAuthorityRule {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(sourceId, "sourceId");
        sourceId = sourceId.map(ProfileAuthorityRule::requireSourceId);
        if (priority <= 0) {
            throw new IllegalArgumentException("profile authority priority must be positive");
        }
    }

    public static ProfileAuthorityRule forKind(ProfileSourceKind kind, int priority) {
        return new ProfileAuthorityRule(kind, Optional.empty(), priority);
    }

    public static ProfileAuthorityRule forSource(
            ProfileSourceKind kind,
            String sourceId,
            int priority
    ) {
        return new ProfileAuthorityRule(kind, Optional.of(sourceId), priority);
    }

    boolean matchesExactly(ProfileValueSource source) {
        return kind == source.kind() && sourceId.filter(source.sourceId()::equals).isPresent();
    }

    boolean matchesKind(ProfileValueSource source) {
        return kind == source.kind() && sourceId.isEmpty();
    }

    private static String requireSourceId(String sourceId) {
        Objects.requireNonNull(sourceId, "sourceId");
        String normalized = sourceId.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        return normalized;
    }
}
