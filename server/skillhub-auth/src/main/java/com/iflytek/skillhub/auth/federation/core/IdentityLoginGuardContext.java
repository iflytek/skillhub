package com.iflytek.skillhub.auth.federation.core;

import com.iflytek.skillhub.domain.user.UserStatus;
import java.util.Objects;
import java.util.Optional;

/** Immutable state inspected immediately before principal or session creation. */
public record IdentityLoginGuardContext(
        UserStatus accountStatus,
        boolean systemAccount,
        Optional<OrganizationLoginState> organizationState,
        Optional<MembershipLoginState> membershipState,
        LoginConnectionGuardState connectionState,
        ExternalIdentityGuardState externalIdentityState
) {
    public IdentityLoginGuardContext {
        Objects.requireNonNull(accountStatus, "accountStatus");
        Objects.requireNonNull(organizationState, "organizationState");
        Objects.requireNonNull(membershipState, "membershipState");
        Objects.requireNonNull(connectionState, "connectionState");
        Objects.requireNonNull(externalIdentityState, "externalIdentityState");
        if (organizationState.isPresent() != membershipState.isPresent()) {
            throw new IllegalArgumentException(
                    "organization and membership states must either both be present or both be absent"
            );
        }
    }
}
