package com.iflytek.skillhub.domain.organization;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Enforces membership, authorization and last-owner invariants for role changes. */
@Service
public class OrganizationRoleBindingService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationRoleBindingRepository roleBindingRepository;
    private final OrganizationAuthorizationService authorizationService;

    public OrganizationRoleBindingService(
            OrganizationRepository organizationRepository,
            OrganizationMembershipRepository membershipRepository,
            OrganizationRoleBindingRepository roleBindingRepository,
            OrganizationAuthorizationService authorizationService
    ) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.roleBindingRepository = roleBindingRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public OrganizationRoleBinding grant(
            String organizationId,
            String targetUserId,
            OrganizationRole role,
            String actorUserId,
            Instant occurredAt
    ) {
        String scopedOrganizationId = requireNonBlank(organizationId, "organizationId");
        String requiredTargetUserId = requireNonBlank(targetUserId, "targetUserId");
        String requiredActorUserId = requireNonBlank(actorUserId, "actorUserId");
        OrganizationRole requiredRole = Objects.requireNonNull(role, "role");
        requireRoleAdministrator(scopedOrganizationId, requiredActorUserId);
        requireActiveMember(scopedOrganizationId, requiredTargetUserId);
        var existing = roleBindingRepository.findActiveByOrganizationIdAndUserIdAndRole(
                scopedOrganizationId,
                requiredTargetUserId,
                requiredRole
        );
        if (existing.isPresent()) {
            return existing.get();
        }

        Organization organization = getOrganization(scopedOrganizationId);
        OrganizationRoleBinding binding = OrganizationRoleBinding.grant(
                scopedOrganizationId,
                requiredTargetUserId,
                requiredRole,
                requiredActorUserId,
                occurredAt
        );
        organization.recordRoleBindingChange(occurredAt);
        OrganizationRoleBinding saved = roleBindingRepository.save(binding);
        organizationRepository.save(organization);
        return saved;
    }

    @Transactional
    public OrganizationRoleBinding revoke(
            String organizationId,
            String bindingId,
            String actorUserId,
            Instant occurredAt
    ) {
        String scopedOrganizationId = requireNonBlank(organizationId, "organizationId");
        String requiredBindingId = requireNonBlank(bindingId, "bindingId");
        String requiredActorUserId = requireNonBlank(actorUserId, "actorUserId");
        requireRoleAdministrator(scopedOrganizationId, requiredActorUserId);
        OrganizationRoleBinding binding = roleBindingRepository
                .findByOrganizationIdAndId(scopedOrganizationId, requiredBindingId)
                .orElseThrow(() -> new DomainNotFoundException(
                        "error.organization.role-binding.not-found"
                ));
        if (binding.getStatus() == OrganizationRoleBindingStatus.REVOKED) {
            return binding;
        }
        if (binding.getRole() == OrganizationRole.ORG_OWNER
                && roleBindingRepository.countActiveByOrganizationIdAndRole(
                        scopedOrganizationId,
                        OrganizationRole.ORG_OWNER
                ) <= 1) {
            throw new DomainConflictException("error.organization.role-binding.last-owner");
        }

        Organization organization = getOrganization(scopedOrganizationId);
        binding.revoke(requiredActorUserId, occurredAt);
        organization.recordRoleBindingChange(occurredAt);
        OrganizationRoleBinding saved = roleBindingRepository.save(binding);
        organizationRepository.save(organization);
        return saved;
    }

    private void requireRoleAdministrator(String organizationId, String actorUserId) {
        authorizationService.requireAllowed(
                organizationId,
                actorUserId,
                OrganizationAdministrativeAction.MANAGE_ORGANIZATION_ROLES
        );
    }

    private void requireActiveMember(String organizationId, String targetUserId) {
        boolean active = membershipRepository
                .findCurrentByOrganizationIdAndUserId(organizationId, targetUserId)
                .filter(membership ->
                        membership.getStatus() == OrganizationMembershipStatus.ACTIVE)
                .isPresent();
        if (!active) {
            throw new DomainConflictException(
                    "error.organization.role-binding.target-not-active"
            );
        }
    }

    private Organization getOrganization(String organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new DomainNotFoundException("error.organization.not-found"));
    }

    private String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainBadRequestException(
                    "error.organization.role-binding.field.required",
                    field
            );
        }
        return value;
    }
}
