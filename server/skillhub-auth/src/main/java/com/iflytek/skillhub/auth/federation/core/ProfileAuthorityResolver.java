package com.iflytek.skillhub.auth.federation.core;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Resolves one profile field update using field-specific, platform-owned authority policy. */
public final class ProfileAuthorityResolver {

    public ProfileFieldResolution resolve(
            Optional<ProfileFieldState> current,
            ProfileFieldCandidate candidate,
            ProfileFieldAuthorityPolicy policy
    ) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(policy, "policy");
        requireSameField(current, candidate, policy);

        OptionalInt candidatePriority = policy.priorityFor(candidate.source());
        if (candidatePriority.isEmpty()) {
            return current.<ProfileFieldResolution>map(state -> new ProfileFieldResolution.Preserved(
                    state,
                    ProfileFieldResolutionReason.SOURCE_NOT_AUTHORIZED
            )).orElseGet(() -> new ProfileFieldResolution.Rejected(
                    ProfileFieldResolutionReason.SOURCE_NOT_AUTHORIZED
            ));
        }

        ProfileFieldState proposed = new ProfileFieldState(
                candidate.field(),
                candidate.value(),
                new ProfileFieldAuthority(candidate.source(), candidatePriority.getAsInt())
        );
        if (current.isEmpty()) {
            return new ProfileFieldResolution.Applied(proposed);
        }

        ProfileFieldState existing = current.orElseThrow();
        int comparison = Integer.compare(candidatePriority.getAsInt(), existing.authority().priority());
        if (comparison > 0 || (comparison == 0
                && candidate.source().equals(existing.authority().source()))) {
            return new ProfileFieldResolution.Applied(proposed);
        }
        ProfileFieldResolutionReason reason = comparison < 0
                ? ProfileFieldResolutionReason.LOWER_AUTHORITY
                : ProfileFieldResolutionReason.EQUAL_AUTHORITY_CONFLICT;
        return new ProfileFieldResolution.Preserved(existing, reason);
    }

    private static void requireSameField(
            Optional<ProfileFieldState> current,
            ProfileFieldCandidate candidate,
            ProfileFieldAuthorityPolicy policy
    ) {
        if (!candidate.field().equals(policy.field())) {
            throw new IllegalArgumentException("candidate field must match authority policy field");
        }
        if (current.isPresent() && !current.orElseThrow().field().equals(candidate.field())) {
            throw new IllegalArgumentException("current and candidate profile fields must match");
        }
    }
}
