package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.audit.OrganizationAuditAction;
import com.iflytek.skillhub.domain.audit.OrganizationAuditDetail;
import com.iflytek.skillhub.domain.audit.OrganizationAuditEvent;
import com.iflytek.skillhub.domain.audit.OrganizationAuditTargetType;
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
import com.iflytek.skillhub.dto.OrganizationResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.repository.EnterpriseIdentityQueryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit platform administration surface for Organization creation and global listing. */
@Service
public class PlatformOrganizationAdminAppService {

    private static final int MAX_PAGE_SIZE = 100;

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationRoleBindingRepository roleBindingRepository;
    private final UserAccountRepository userAccountRepository;
    private final EnterpriseIdentityQueryRepository queryRepository;
    private final AuditLogService auditLogService;
    private final RequestIdAccessor requestIdAccessor;
    private final Clock clock;

    public PlatformOrganizationAdminAppService(
            OrganizationRepository organizationRepository,
            OrganizationMembershipRepository membershipRepository,
            OrganizationRoleBindingRepository roleBindingRepository,
            UserAccountRepository userAccountRepository,
            EnterpriseIdentityQueryRepository queryRepository,
            AuditLogService auditLogService,
            RequestIdAccessor requestIdAccessor,
            Clock clock
    ) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.roleBindingRepository = roleBindingRepository;
        this.userAccountRepository = userAccountRepository;
        this.queryRepository = queryRepository;
        this.auditLogService = auditLogService;
        this.requestIdAccessor = requestIdAccessor;
        this.clock = clock;
    }

    @Transactional
    public OrganizationResponse create(
            OrganizationCreateRequest request,
            String actorUserId
    ) {
        UserAccount owner = requireEligibleOwner(request.initialOwnerUserId());
        organizationRepository.findBySlug(request.slug()).ifPresent(existing -> {
            throw new DomainConflictException("error.organization.slug.conflict");
        });
        Instant now = clock.instant();
        Organization organization = Organization.create(
                request.slug(),
                request.displayName(),
                actorUserId,
                now
        );
        organizationRepository.save(organization);

        OrganizationMembership membership = OrganizationMembership.provisioned(
                organization.getId(),
                MembershipSourceType.MANUAL,
                owner.getId(),
                owner.getDisplayName(),
                owner.getEmail(),
                now
        );
        membership.activate(owner.getId(), now);
        membershipRepository.save(membership);

        OrganizationRoleBinding ownerBinding = OrganizationRoleBinding.grant(
                organization.getId(),
                owner.getId(),
                OrganizationRole.ORG_OWNER,
                actorUserId,
                now
        );
        roleBindingRepository.save(ownerBinding);
        organization.recordRoleBindingChange(now);
        organizationRepository.save(organization);
        auditLogService.record(OrganizationAuditEvent.success(
                actorUserId,
                organization.getId(),
                OrganizationAuditAction.ORGANIZATION_CREATED,
                OrganizationAuditTargetType.ORGANIZATION,
                organization.getId(),
                requestIdAccessor.current(),
                OrganizationAuditDetail.transition(null, organization.getStatus().name())
        ));
        return OrganizationResponse.from(organization, List.of());
    }

    @Transactional(readOnly = true)
    public PageResponse<OrganizationResponse> list(int page, int size) {
        Page<OrganizationResponse> result = queryRepository.findOrganizations(pageRequest(page, size))
                .map(organization -> OrganizationResponse.from(organization, List.of()));
        return PageResponse.from(result);
    }

    private UserAccount requireEligibleOwner(String userId) {
        UserAccount account = userAccountRepository.findById(userId)
                .orElseThrow(() -> new DomainNotFoundException(
                        "error.organization.owner.not-found"
                ));
        if (!account.isActive()
                || account.isSystemAccount()
                || account.getMergedToUserId() != null) {
            throw new DomainConflictException("error.organization.owner.not-eligible");
        }
        return account;
    }

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }
}
