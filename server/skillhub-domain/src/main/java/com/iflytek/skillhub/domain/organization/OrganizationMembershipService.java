package com.iflytek.skillhub.domain.organization;

import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns manual membership creation and guarded administrator-driven lifecycle changes. */
@Service
public class OrganizationMembershipService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationRoleBindingRepository roleBindingRepository;
    private final UserAccountRepository userAccountRepository;
    private final OrganizationAuthorizationService authorizationService;

    public OrganizationMembershipService(
            OrganizationRepository organizationRepository,
            OrganizationMembershipRepository membershipRepository,
            OrganizationRoleBindingRepository roleBindingRepository,
            UserAccountRepository userAccountRepository,
            OrganizationAuthorizationService authorizationService
    ) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.roleBindingRepository = roleBindingRepository;
        this.userAccountRepository = userAccountRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public OrganizationMembership addManualMember(
            String organizationId,
            String targetUserId,
            String actorUserId,
            Instant occurredAt
    ) {
        authorize(organizationId, actorUserId);
        requireActiveOrganization(organizationId);
        UserAccount account = requireEligibleAccount(targetUserId);
        membershipRepository.findCurrentByOrganizationIdAndUserId(
                organizationId,
                targetUserId
        ).ifPresent(existing -> {
            throw new DomainConflictException(
                    "error.organization.membership.already-exists"
            );
        });

        OrganizationMembership membership = OrganizationMembership.provisioned(
                organizationId,
                MembershipSourceType.MANUAL,
                targetUserId,
                account.getDisplayName(),
                account.getEmail(),
                occurredAt
        );
        membership.activate(targetUserId, occurredAt);
        return membershipRepository.save(membership);
    }

    @Transactional
    public OrganizationMembership preProvisionMember(
            String organizationId,
            String displayName,
            String primaryEmail,
            String actorUserId,
            Instant occurredAt
    ) {
        authorize(organizationId, actorUserId);
        requireActiveOrganization(organizationId);
        OrganizationMembership membership = OrganizationMembership.provisioned(
                organizationId,
                MembershipSourceType.MANUAL,
                null,
                displayName,
                primaryEmail,
                occurredAt
        );
        return membershipRepository.save(membership);
    }

    @Transactional
    public OrganizationMembership suspend(
            String organizationId,
            String membershipId,
            String actorUserId,
            Instant occurredAt
    ) {
        authorize(organizationId, actorUserId);
        OrganizationMembership membership = getMembership(organizationId, membershipId);
        if (membership.getStatus() == OrganizationMembershipStatus.SUSPENDED) {
            return membership;
        }
        requireNoActiveOwnerRole(organizationId, membership);
        membership.suspend(occurredAt);
        return membershipRepository.save(membership);
    }

    @Transactional
    public OrganizationMembership reactivate(
            String organizationId,
            String membershipId,
            String actorUserId,
            Instant occurredAt
    ) {
        authorize(organizationId, actorUserId);
        requireActiveOrganization(organizationId);
        OrganizationMembership membership = getMembership(organizationId, membershipId);
        UserAccount account = requireEligibleAccount(membership.getUserId());
        membership.activate(account.getId(), occurredAt);
        return membershipRepository.save(membership);
    }

    @Transactional
    public OrganizationMembership deprovision(
            String organizationId,
            String membershipId,
            String actorUserId,
            Instant occurredAt
    ) {
        authorize(organizationId, actorUserId);
        OrganizationMembership membership = getMembership(organizationId, membershipId);
        if (membership.getStatus() == OrganizationMembershipStatus.DEPROVISIONED) {
            return membership;
        }
        requireNoActiveOwnerRole(organizationId, membership);

        List<OrganizationRoleBinding> bindings = membership.getUserId() == null
                ? List.of()
                : roleBindingRepository.findActiveByOrganizationIdAndUserId(
                        organizationId,
                        membership.getUserId()
                );
        if (!bindings.isEmpty()) {
            Organization organization = getOrganization(organizationId);
            bindings.forEach(binding -> {
                binding.revoke(actorUserId, occurredAt);
                roleBindingRepository.save(binding);
            });
            organization.recordRoleBindingChange(occurredAt);
            organizationRepository.save(organization);
        }
        membership.deprovision(occurredAt);
        return membershipRepository.save(membership);
    }

    private void authorize(String organizationId, String actorUserId) {
        authorizationService.requireAllowed(
                organizationId,
                actorUserId,
                OrganizationAdministrativeAction.MANAGE_MEMBERS
        );
    }

    private void requireNoActiveOwnerRole(
            String organizationId,
            OrganizationMembership membership
    ) {
        if (membership.getUserId() == null) {
            return;
        }
        boolean hasActiveOwnerRole = roleBindingRepository.findActiveByOrganizationIdAndUserId(
                organizationId,
                membership.getUserId()
        ).stream().anyMatch(binding -> binding.getRole() == OrganizationRole.ORG_OWNER);
        if (hasActiveOwnerRole) {
            throw new DomainConflictException(
                    "error.organization.membership.owner-role-active"
            );
        }
    }

    private UserAccount requireEligibleAccount(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new DomainConflictException(
                    "error.organization.membership.account-not-eligible"
            );
        }
        UserAccount account = userAccountRepository.findById(userId)
                .orElseThrow(() -> new DomainNotFoundException(
                        "error.organization.membership.account-not-found"
                ));
        if (!account.isActive()
                || account.isSystemAccount()
                || account.getMergedToUserId() != null) {
            throw new DomainConflictException(
                    "error.organization.membership.account-not-eligible"
            );
        }
        return account;
    }

    private void requireActiveOrganization(String organizationId) {
        if (getOrganization(organizationId).getStatus() != OrganizationStatus.ACTIVE) {
            throw new DomainConflictException("error.organization.inactive");
        }
    }

    private OrganizationMembership getMembership(String organizationId, String membershipId) {
        return membershipRepository.findByOrganizationIdAndId(organizationId, membershipId)
                .orElseThrow(() -> new DomainNotFoundException(
                        "error.organization.membership.not-found"
                ));
    }

    private Organization getOrganization(String organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new DomainNotFoundException("error.organization.not-found"));
    }
}
