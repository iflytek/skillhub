package com.iflytek.skillhub.domain.organization;

import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Resolves one fail-closed, tenant-scoped authorization snapshot for a Platform Account. */
@Service
public class OrganizationAccessGuard {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;

    public OrganizationAccessGuard(
            OrganizationRepository organizationRepository,
            OrganizationMembershipRepository membershipRepository
    ) {
        this.organizationRepository = Objects.requireNonNull(
                organizationRepository,
                "organizationRepository"
        );
        this.membershipRepository = Objects.requireNonNull(
                membershipRepository,
                "membershipRepository"
        );
    }

    public OrganizationAccessSnapshot requireCurrent(String organizationId, String userId) {
        return requireCurrent(organizationId, userId, true);
    }

    OrganizationAccessSnapshot requireCurrentForLifecycleRecovery(
            String organizationId,
            String userId
    ) {
        return requireCurrent(organizationId, userId, false);
    }

    private OrganizationAccessSnapshot requireCurrent(
            String organizationId,
            String userId,
            boolean requireActiveOrganization
    ) {
        String tenant = requireText(organizationId, "organizationId");
        String subject = requireText(userId, "userId");
        Organization organization = organizationRepository.findById(tenant)
                .orElseThrow(OrganizationAccessGuard::denied);
        if (requireActiveOrganization && organization.getStatus() != OrganizationStatus.ACTIVE) {
            throw denied();
        }
        OrganizationMembership membership = membershipRepository
                .findCurrentByOrganizationIdAndUserId(tenant, subject)
                .filter(candidate -> candidate.getStatus() == OrganizationMembershipStatus.ACTIVE)
                .filter(candidate -> subject.equals(candidate.getUserId()))
                .orElseThrow(OrganizationAccessGuard::denied);
        return new OrganizationAccessSnapshot(
                tenant,
                membership.getId(),
                subject,
                organization.getAuthorityVersion(),
                membership.getAuthorityVersion()
        );
    }

    private static DomainForbiddenException denied() {
        return new DomainForbiddenException("error.organization.permission.denied");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
