package com.iflytek.skillhub.auth.federation.core;

import com.iflytek.skillhub.domain.user.UserStatus;
import java.util.Objects;
import java.util.function.Function;

/** Final, protocol-neutral gate before an authenticated principal or session may be created. */
public final class IdentityLoginGuard {

    public ApprovedIdentityLogin requireAllowed(IdentityLoginGuardContext context) {
        Objects.requireNonNull(context, "context");
        requireAccountAllowed(context);
        context.organizationState().ifPresent(this::requireOrganizationAllowed);
        context.membershipState().ifPresent(this::requireMembershipAllowed);
        requireConnectionAllowed(context.connectionState());
        requireExternalIdentityAllowed(context.externalIdentityState());
        return new ApprovedIdentityLogin(context);
    }

    public <T> T continueAfterApproval(
            IdentityLoginGuardContext context,
            Function<ApprovedIdentityLogin, T> continuation
    ) {
        Objects.requireNonNull(continuation, "continuation");
        return continuation.apply(requireAllowed(context));
    }

    private static void requireAccountAllowed(IdentityLoginGuardContext context) {
        if (context.systemAccount()) {
            reject(IdentityLoginRejectionReason.SYSTEM_ACCOUNT_FORBIDDEN);
        }
        if (context.accountStatus() == UserStatus.PENDING) {
            reject(IdentityLoginRejectionReason.ACCOUNT_PENDING);
        }
        if (context.accountStatus() == UserStatus.DISABLED) {
            reject(IdentityLoginRejectionReason.ACCOUNT_DISABLED);
        }
        if (context.accountStatus() == UserStatus.MERGED) {
            reject(IdentityLoginRejectionReason.ACCOUNT_MERGED);
        }
    }

    private void requireOrganizationAllowed(OrganizationLoginState state) {
        if (state == OrganizationLoginState.SUSPENDED) {
            reject(IdentityLoginRejectionReason.ORGANIZATION_SUSPENDED);
        }
        if (state == OrganizationLoginState.DECOMMISSIONED) {
            reject(IdentityLoginRejectionReason.ORGANIZATION_DECOMMISSIONED);
        }
    }

    private void requireMembershipAllowed(MembershipLoginState state) {
        switch (state) {
            case ACTIVE -> {
            }
            case INVITED, PROVISIONED -> reject(IdentityLoginRejectionReason.MEMBERSHIP_NOT_ACTIVE);
            case SUSPENDED -> reject(IdentityLoginRejectionReason.MEMBERSHIP_SUSPENDED);
            case DEPROVISIONED -> reject(IdentityLoginRejectionReason.MEMBERSHIP_DEPROVISIONED);
        }
    }

    private void requireConnectionAllowed(LoginConnectionGuardState state) {
        if (state != LoginConnectionGuardState.ACTIVE) {
            reject(IdentityLoginRejectionReason.CONNECTION_NOT_ACTIVE);
        }
    }

    private void requireExternalIdentityAllowed(ExternalIdentityGuardState state) {
        if (state == ExternalIdentityGuardState.SUSPENDED) {
            reject(IdentityLoginRejectionReason.EXTERNAL_IDENTITY_SUSPENDED);
        }
        if (state == ExternalIdentityGuardState.REVOKED) {
            reject(IdentityLoginRejectionReason.EXTERNAL_IDENTITY_REVOKED);
        }
    }

    private static void reject(IdentityLoginRejectionReason reason) {
        throw new IdentityLoginRejectedException(reason);
    }
}
