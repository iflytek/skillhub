package com.iflytek.skillhub.domain.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganizationMembershipServiceTest {

    private static final String ORGANIZATION_ID = "organization-a";
    private static final String ACTOR_ID = "directory-admin";
    private static final String TARGET_ID = "target-user";
    private static final Instant CREATED_AT = Instant.parse("2026-09-08T00:00:00Z");
    private static final Instant CHANGED_AT = CREATED_AT.plusSeconds(60);

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMembershipRepository membershipRepository;

    @Mock
    private OrganizationRoleBindingRepository roleBindingRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private OrganizationAccessGuard accessGuard;

    private OrganizationMembershipService service;

    @BeforeEach
    void setUp() {
        service = new OrganizationMembershipService(
                organizationRepository,
                membershipRepository,
                roleBindingRepository,
                userAccountRepository,
                new OrganizationAuthorizationService(
                        accessGuard,
                        roleBindingRepository,
                        new OrganizationAuthorizationPolicy()
                )
        );
    }

    @Test
    void manualMemberMustBeAnExistingLoginCapableAccount() {
        givenDirectoryAdministrator();
        given(organizationRepository.findById(ORGANIZATION_ID))
                .willReturn(Optional.of(organization()));
        given(userAccountRepository.findById(TARGET_ID))
                .willReturn(Optional.of(new UserAccount(
                        TARGET_ID,
                        "Target User",
                        "target@example.com",
                        null
                )));
        given(membershipRepository.findCurrentByOrganizationIdAndUserId(
                ORGANIZATION_ID,
                TARGET_ID
        )).willReturn(Optional.empty());
        given(membershipRepository.save(org.mockito.ArgumentMatchers.any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        OrganizationMembership result = service.addManualMember(
                ORGANIZATION_ID,
                TARGET_ID,
                ACTOR_ID,
                CHANGED_AT
        );

        assertThat(result.getStatus()).isEqualTo(OrganizationMembershipStatus.ACTIVE);
        assertThat(result.getSourceType()).isEqualTo(MembershipSourceType.MANUAL);
        assertThat(result.getUserId()).isEqualTo(TARGET_ID);
        assertThat(result.getDisplayName()).isEqualTo("Target User");
        assertThat(result.getPrimaryEmail()).isEqualTo("target@example.com");
    }

    @Test
    void systemAccountCannotBecomeAManualMember() {
        givenDirectoryAdministrator();
        given(organizationRepository.findById(ORGANIZATION_ID))
                .willReturn(Optional.of(organization()));
        given(userAccountRepository.findById(TARGET_ID))
                .willReturn(Optional.of(UserAccount.systemAccount(
                        TARGET_ID,
                        "System",
                        null,
                        null
                )));

        assertThatThrownBy(() -> service.addManualMember(
                ORGANIZATION_ID,
                TARGET_ID,
                ACTOR_ID,
                CHANGED_AT
        ))
                .isInstanceOf(DomainConflictException.class)
                .hasMessage("error.organization.membership.account-not-eligible");

        verify(membershipRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void activeOwnerRoleMustBeRevokedBeforeSuspendingMembership() {
        OrganizationMembership owner = activeMembership(TARGET_ID);
        givenDirectoryAdministrator();
        given(membershipRepository.findByOrganizationIdAndId(
                ORGANIZATION_ID,
                owner.getId()
        )).willReturn(Optional.of(owner));
        given(roleBindingRepository.findActiveByOrganizationIdAndUserId(
                ORGANIZATION_ID,
                TARGET_ID
        )).willReturn(List.of(binding(TARGET_ID, OrganizationRole.ORG_OWNER)));

        assertThatThrownBy(() -> service.suspend(
                ORGANIZATION_ID,
                owner.getId(),
                ACTOR_ID,
                CHANGED_AT
        ))
                .isInstanceOf(DomainConflictException.class)
                .hasMessage("error.organization.membership.owner-role-active");

        assertThat(owner.getStatus()).isEqualTo(OrganizationMembershipStatus.ACTIVE);
    }

    @Test
    void deprovisionRevokesRemainingRolesAndAdvancesOrganizationAuthorityOnce() {
        Organization organization = organization();
        OrganizationMembership member = activeMembership(TARGET_ID);
        OrganizationRoleBinding first = binding(TARGET_ID, OrganizationRole.IDENTITY_ADMIN);
        OrganizationRoleBinding second = binding(TARGET_ID, OrganizationRole.ORG_AUDITOR);
        givenDirectoryAdministrator();
        given(membershipRepository.findByOrganizationIdAndId(
                ORGANIZATION_ID,
                member.getId()
        )).willReturn(Optional.of(member));
        given(roleBindingRepository.findActiveByOrganizationIdAndUserId(
                ORGANIZATION_ID,
                TARGET_ID
        )).willReturn(List.of(first, second));
        given(organizationRepository.findById(ORGANIZATION_ID))
                .willReturn(Optional.of(organization));
        given(membershipRepository.save(member)).willReturn(member);

        OrganizationMembership result = service.deprovision(
                ORGANIZATION_ID,
                member.getId(),
                ACTOR_ID,
                CHANGED_AT
        );

        assertThat(result.getStatus()).isEqualTo(OrganizationMembershipStatus.DEPROVISIONED);
        assertThat(first.getStatus()).isEqualTo(OrganizationRoleBindingStatus.REVOKED);
        assertThat(second.getStatus()).isEqualTo(OrganizationRoleBindingStatus.REVOKED);
        assertThat(organization.getAuthorityVersion()).isEqualTo(1);
        verify(roleBindingRepository).save(first);
        verify(roleBindingRepository).save(second);
        verify(organizationRepository).save(organization);
    }

    private void givenDirectoryAdministrator() {
        given(roleBindingRepository.findActiveByOrganizationIdAndUserId(
                ORGANIZATION_ID,
                ACTOR_ID
        )).willReturn(List.of(binding(ACTOR_ID, OrganizationRole.MEMBER_ADMIN)));
    }

    private Organization organization() {
        return Organization.create(
                "organization-a",
                "Organization A",
                ACTOR_ID,
                CREATED_AT
        );
    }

    private OrganizationMembership activeMembership(String userId) {
        OrganizationMembership membership = OrganizationMembership.provisioned(
                ORGANIZATION_ID,
                MembershipSourceType.MANUAL,
                userId,
                userId,
                userId + "@example.com",
                CREATED_AT
        );
        membership.activate(userId, CREATED_AT);
        return membership;
    }

    private OrganizationRoleBinding binding(String userId, OrganizationRole role) {
        return OrganizationRoleBinding.grant(
                ORGANIZATION_ID,
                userId,
                role,
                ACTOR_ID,
                CREATED_AT
        );
    }
}
