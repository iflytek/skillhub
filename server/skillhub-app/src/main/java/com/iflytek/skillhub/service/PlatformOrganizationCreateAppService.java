package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.organization.MembershipSourceType;
import com.iflytek.skillhub.domain.organization.Organization;
import com.iflytek.skillhub.domain.organization.OrganizationMembership;
import com.iflytek.skillhub.domain.organization.OrganizationMembershipRepository;
import com.iflytek.skillhub.domain.organization.OrganizationRepository;
import com.iflytek.skillhub.domain.organization.OrganizationRole;
import com.iflytek.skillhub.domain.organization.OrganizationRoleBinding;
import com.iflytek.skillhub.domain.organization.OrganizationRoleBindingRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.OrganizationCreateRequest;
import com.iflytek.skillhub.dto.OrganizationCreateResponse;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates the tenant boundary and its first owner as one transaction. */
@Service
public class PlatformOrganizationCreateAppService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationRoleBindingRepository roleBindingRepository;
    private final UserAccountRepository userAccountRepository;
    private final AuditLogService auditLogService;
    private final RequestIdAccessor requestIdAccessor;
    private final Clock clock;

    public PlatformOrganizationCreateAppService(
            OrganizationRepository organizationRepository,
            OrganizationMembershipRepository membershipRepository,
            OrganizationRoleBindingRepository roleBindingRepository,
            UserAccountRepository userAccountRepository,
            AuditLogService auditLogService,
            RequestIdAccessor requestIdAccessor,
            Clock clock
    ) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.roleBindingRepository = roleBindingRepository;
        this.userAccountRepository = userAccountRepository;
        this.auditLogService = auditLogService;
        this.requestIdAccessor = requestIdAccessor;
        this.clock = clock;
    }

    @Transactional
    public OrganizationCreateResponse create(OrganizationCreateRequest request, String actorUserId) {
        UserAccount owner = userAccountRepository.findById(request.initialOwnerUserId())
                .orElseThrow(() -> new DomainNotFoundException("error.organization.owner.not-found"));
        if (!owner.isActive() || owner.isSystemAccount() || owner.getMergedToUserId() != null) {
            throw new DomainConflictException("error.organization.owner.not-eligible");
        }
        organizationRepository.findBySlug(request.slug()).ifPresent(existing -> {
            throw new DomainConflictException("error.organization.slug.conflict");
        });

        Instant now = clock.instant();
        Organization organization = organizationRepository.save(Organization.create(
                request.slug(), request.displayName(), actorUserId, now));
        OrganizationMembership membership = OrganizationMembership.provisioned(
                organization.getId(), MembershipSourceType.MANUAL, owner.getId(),
                owner.getDisplayName(), owner.getEmail(), now);
        membership.activate(owner.getId(), now);
        membershipRepository.save(membership);
        roleBindingRepository.save(OrganizationRoleBinding.grant(
                organization.getId(), owner.getId(), OrganizationRole.ORG_OWNER, actorUserId, now));
        organization.recordRoleBindingChange(now);
        organizationRepository.save(organization);
        auditLogService.recordOrganizationCreated(actorUserId, organization.getId(), requestIdAccessor.current());
        return OrganizationCreateResponse.from(organization);
    }
}
