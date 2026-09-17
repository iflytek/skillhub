package com.iflytek.skillhub.auth.federation.core;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/** Allowlist and deterministic priority rules for one field coordinate. */
public record ProfileFieldAuthorityPolicy(
        ProfileFieldCoordinate field,
        List<ProfileAuthorityRule> rules
) {
    public ProfileFieldAuthorityPolicy {
        Objects.requireNonNull(field, "field");
        rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
        Set<RuleSelector> selectors = new HashSet<>();
        for (ProfileAuthorityRule rule : rules) {
            Objects.requireNonNull(rule, "profile authority rule must not be null");
            RuleSelector selector = new RuleSelector(rule.kind(), rule.sourceId().orElse(null));
            if (!selectors.add(selector)) {
                throw new IllegalArgumentException("duplicate profile authority rule: " + selector);
            }
            if (field.scope() instanceof ProfileScope.Global
                    && rule.kind() == ProfileSourceKind.ORGANIZATION_ADMIN) {
                throw new IllegalArgumentException(
                        "organization-managed source cannot have authority over a global profile field"
                );
            }
        }
    }

    OptionalInt priorityFor(ProfileValueSource source) {
        for (ProfileAuthorityRule rule : rules) {
            if (rule.matchesExactly(source)) {
                return OptionalInt.of(rule.priority());
            }
        }
        for (ProfileAuthorityRule rule : rules) {
            if (rule.matchesKind(source)) {
                return OptionalInt.of(rule.priority());
            }
        }
        return OptionalInt.empty();
    }

    private record RuleSelector(ProfileSourceKind kind, String sourceId) {
    }
}
