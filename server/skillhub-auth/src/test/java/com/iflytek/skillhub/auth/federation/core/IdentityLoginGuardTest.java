package com.iflytek.skillhub.auth.federation.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.domain.user.UserStatus;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class IdentityLoginGuardTest {

    private final IdentityLoginGuard guard = new IdentityLoginGuard();

    @Test
    void activePlatformAndEnterpriseContextsCanContinueToPrincipalCreation() {
        String platformPrincipal = guard.continueAfterApproval(
                platformContext(),
                approved -> "platform-principal"
        );
        String enterprisePrincipal = guard.continueAfterApproval(
                enterpriseContext(),
                approved -> "enterprise-principal"
        );

        assertThat(platformPrincipal).isEqualTo("platform-principal");
        assertThat(enterprisePrincipal).isEqualTo("enterprise-principal");
    }

    @Test
    void disabledMergedAndSystemAccountsAreRejectedBeforeContinuation() {
        assertRejected(
                withAccount(UserStatus.PENDING, false),
                IdentityLoginRejectionReason.ACCOUNT_PENDING
        );
        assertRejected(
                withAccount(UserStatus.DISABLED, false),
                IdentityLoginRejectionReason.ACCOUNT_DISABLED
        );
        assertRejected(
                withAccount(UserStatus.MERGED, false),
                IdentityLoginRejectionReason.ACCOUNT_MERGED
        );
        assertRejected(
                withAccount(UserStatus.ACTIVE, true),
                IdentityLoginRejectionReason.SYSTEM_ACCOUNT_FORBIDDEN
        );
    }

    @Test
    void suspendedOrganizationAndMembershipAreRejectedBeforeContinuation() {
        assertRejected(
                withOrganization(OrganizationLoginState.SUSPENDED),
                IdentityLoginRejectionReason.ORGANIZATION_SUSPENDED
        );
        assertRejected(
                withOrganization(OrganizationLoginState.DECOMMISSIONED),
                IdentityLoginRejectionReason.ORGANIZATION_DECOMMISSIONED
        );
        assertRejected(
                withMembership(MembershipLoginState.INVITED),
                IdentityLoginRejectionReason.MEMBERSHIP_NOT_ACTIVE
        );
        assertRejected(
                withMembership(MembershipLoginState.PROVISIONED),
                IdentityLoginRejectionReason.MEMBERSHIP_NOT_ACTIVE
        );
        assertRejected(
                withMembership(MembershipLoginState.SUSPENDED),
                IdentityLoginRejectionReason.MEMBERSHIP_SUSPENDED
        );
    }

    @Test
    void deprovisionedMembershipCannotBeRestoredByOrdinaryLogin() {
        assertRejected(
                withMembership(MembershipLoginState.DEPROVISIONED),
                IdentityLoginRejectionReason.MEMBERSHIP_DEPROVISIONED
        );
    }

    @Test
    void inactiveConnectionAndExternalIdentityAreRejectedBeforeContinuation() {
        assertRejected(
                withConnection(LoginConnectionGuardState.DISABLED),
                IdentityLoginRejectionReason.CONNECTION_NOT_ACTIVE
        );
        assertRejected(
                withConnection(LoginConnectionGuardState.DRAFT),
                IdentityLoginRejectionReason.CONNECTION_NOT_ACTIVE
        );
        assertRejected(
                withConnection(LoginConnectionGuardState.SUSPENDED),
                IdentityLoginRejectionReason.CONNECTION_NOT_ACTIVE
        );
        assertRejected(
                withConnection(LoginConnectionGuardState.ERROR),
                IdentityLoginRejectionReason.CONNECTION_NOT_ACTIVE
        );
        assertRejected(
                withExternalIdentity(ExternalIdentityGuardState.SUSPENDED),
                IdentityLoginRejectionReason.EXTERNAL_IDENTITY_SUSPENDED
        );
        assertRejected(
                withExternalIdentity(ExternalIdentityGuardState.REVOKED),
                IdentityLoginRejectionReason.EXTERNAL_IDENTITY_REVOKED
        );
    }

    @Test
    void organizationAndMembershipMustBePresentTogether() {
        assertThatThrownBy(() -> new IdentityLoginGuardContext(
                UserStatus.ACTIVE,
                false,
                Optional.of(OrganizationLoginState.ACTIVE),
                Optional.empty(),
                LoginConnectionGuardState.ACTIVE,
                ExternalIdentityGuardState.ACTIVE
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("organization and membership");
    }

    private void assertRejected(
            IdentityLoginGuardContext context,
            IdentityLoginRejectionReason expectedReason
    ) {
        AtomicBoolean continuationInvoked = new AtomicBoolean();

        assertThatThrownBy(() -> guard.continueAfterApproval(context, approved -> {
            continuationInvoked.set(true);
            return "principal-or-session";
        })).isInstanceOf(IdentityLoginRejectedException.class)
                .extracting("reason")
                .isEqualTo(expectedReason);
        assertThat(continuationInvoked).isFalse();
    }

    private static IdentityLoginGuardContext platformContext() {
        return new IdentityLoginGuardContext(
                UserStatus.ACTIVE,
                false,
                Optional.empty(),
                Optional.empty(),
                LoginConnectionGuardState.ACTIVE,
                ExternalIdentityGuardState.ACTIVE
        );
    }

    private static IdentityLoginGuardContext enterpriseContext() {
        return new IdentityLoginGuardContext(
                UserStatus.ACTIVE,
                false,
                Optional.of(OrganizationLoginState.ACTIVE),
                Optional.of(MembershipLoginState.ACTIVE),
                LoginConnectionGuardState.ACTIVE,
                ExternalIdentityGuardState.ACTIVE
        );
    }

    private static IdentityLoginGuardContext withAccount(UserStatus status, boolean systemAccount) {
        IdentityLoginGuardContext context = enterpriseContext();
        return new IdentityLoginGuardContext(
                status,
                systemAccount,
                context.organizationState(),
                context.membershipState(),
                context.connectionState(),
                context.externalIdentityState()
        );
    }

    private static IdentityLoginGuardContext withOrganization(OrganizationLoginState state) {
        IdentityLoginGuardContext context = enterpriseContext();
        return new IdentityLoginGuardContext(
                context.accountStatus(),
                context.systemAccount(),
                Optional.of(state),
                context.membershipState(),
                context.connectionState(),
                context.externalIdentityState()
        );
    }

    private static IdentityLoginGuardContext withMembership(MembershipLoginState state) {
        IdentityLoginGuardContext context = enterpriseContext();
        return new IdentityLoginGuardContext(
                context.accountStatus(),
                context.systemAccount(),
                context.organizationState(),
                Optional.of(state),
                context.connectionState(),
                context.externalIdentityState()
        );
    }

    private static IdentityLoginGuardContext withConnection(LoginConnectionGuardState state) {
        IdentityLoginGuardContext context = enterpriseContext();
        return new IdentityLoginGuardContext(
                context.accountStatus(),
                context.systemAccount(),
                context.organizationState(),
                context.membershipState(),
                state,
                context.externalIdentityState()
        );
    }

    private static IdentityLoginGuardContext withExternalIdentity(ExternalIdentityGuardState state) {
        IdentityLoginGuardContext context = enterpriseContext();
        return new IdentityLoginGuardContext(
                context.accountStatus(),
                context.systemAccount(),
                context.organizationState(),
                context.membershipState(),
                context.connectionState(),
                state
        );
    }
}
