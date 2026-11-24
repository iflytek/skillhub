package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.organization.OrganizationAdministrativeAction;
import com.iflytek.skillhub.domain.organization.OrganizationAuthorizationPolicy;
import com.iflytek.skillhub.domain.organization.OrganizationAuthorizationService;
import com.iflytek.skillhub.domain.organization.OrganizationMembership;
import com.iflytek.skillhub.domain.organization.OrganizationMembershipService;
import com.iflytek.skillhub.domain.organization.OrganizationRepository;
import com.iflytek.skillhub.domain.organization.OrganizationRole;
import com.iflytek.skillhub.domain.organization.OrganizationRoleBindingService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.dto.OrganizationMemberResponse;
import com.iflytek.skillhub.dto.OrganizationRoleBindingResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.repository.OrganizationQueryRepository;
import com.iflytek.skillhub.repository.OrganizationQueryRepository.OrganizationMemberProjection;
import com.iflytek.skillhub.repository.OrganizationQueryRepository.OrganizationMemberRoleProjection;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tenant-scoped member and role administration using the existing domain guards. */
@Service
public class OrganizationMemberAdminAppService {

    private final OrganizationAuthorizationService authorizationService;
    private final OrganizationAuthorizationPolicy authorizationPolicy;
    private final OrganizationQueryRepository queryRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipService membershipService;
    private final OrganizationRoleBindingService roleBindingService;
    private final AuditLogService auditLogService;
    private final RequestIdAccessor requestIdAccessor;
    private final Clock clock;

    public OrganizationMemberAdminAppService(
            OrganizationAuthorizationService authorizationService,
            OrganizationAuthorizationPolicy authorizationPolicy,
            OrganizationQueryRepository queryRepository,
            OrganizationRepository organizationRepository,
            OrganizationMembershipService membershipService,
            OrganizationRoleBindingService roleBindingService,
            AuditLogService auditLogService,
            RequestIdAccessor requestIdAccessor,
            Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.authorizationPolicy = authorizationPolicy;
        this.queryRepository = queryRepository;
        this.organizationRepository = organizationRepository;
        this.membershipService = membershipService;
        this.roleBindingService = roleBindingService;
        this.auditLogService = auditLogService;
        this.requestIdAccessor = requestIdAccessor;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrganizationMemberResponse> listMembers(
            String organizationId, String actorUserId, int page, int size
    ) {
        var actorRoles = authorizationService.requireAllowed(
                organizationId, actorUserId, OrganizationAdministrativeAction.VIEW_MEMBERS);
        if (page < 0 || size < 1) {
            throw new DomainBadRequestException("error.pagination.invalid");
        }
        PageRequest pagination = PageRequest.of(page, Math.min(size, 100));
        if (pagination.getOffset() > Integer.MAX_VALUE) {
            throw new DomainBadRequestException("error.pagination.invalid");
        }
        var members = queryRepository.findCurrentMembers(organizationId, pagination);
        Map<String, List<OrganizationMemberRoleProjection>> roles = authorizationPolicy.isAllowed(
                actorRoles, OrganizationAdministrativeAction.VIEW_ORGANIZATION_ROLES)
                ? queryRepository.findActiveMemberRoles(organizationId,
                        members.stream().map(OrganizationMemberProjection::userId)
                                .filter(Objects::nonNull).toList())
                : Map.of();
        return PageResponse.from(members.map(member -> {
            var memberRoles = member.userId() == null
                    ? List.<OrganizationMemberRoleProjection>of()
                    : roles.getOrDefault(member.userId(), List.of());
            return new OrganizationMemberResponse(
                    member.id(), member.userId(), member.displayName(),
                    member.status(), member.authorityVersion(), member.createdAt(),
                    memberRoles.stream()
                            .map(role -> new OrganizationRoleBindingResponse(
                                    role.id(), role.userId(), role.role(), "ACTIVE"))
                            .toList());
        }));
    }

    @Transactional
    public OrganizationMemberResponse addMember(
            String organizationId, String targetUserId, String actorUserId
    ) {
        lockOrganization(organizationId);
        Instant now = clock.instant();
        OrganizationMembership member = membershipService.addManualMember(
                organizationId, targetUserId, actorUserId, now);
        auditLogService.recordOrganizationAdministration(actorUserId, "ORGANIZATION_MEMBER_ADDED",
                organizationId, member.getId(), requestIdAccessor.current());
        return new OrganizationMemberResponse(
                member.getId(), member.getUserId(), member.getDisplayName(),
                member.getStatus().name(),
                member.getAuthorityVersion(), member.getCreatedAt(), List.of());
    }

    @Transactional
    public OrganizationRoleBindingResponse grantRole(
            String organizationId, String targetUserId, OrganizationRole role, String actorUserId
    ) {
        lockOrganization(organizationId);
        var change = roleBindingService.grantWithResult(
                organizationId, targetUserId, role, actorUserId, clock.instant());
        if (change.changed()) {
            auditLogService.recordOrganizationAdministration(actorUserId, "ORGANIZATION_ROLE_GRANTED",
                    organizationId, change.binding().getId(), requestIdAccessor.current());
        }
        return OrganizationRoleBindingResponse.from(change.binding());
    }

    @Transactional
    public OrganizationRoleBindingResponse revokeRole(
            String organizationId, String bindingId, String actorUserId
    ) {
        lockOrganization(organizationId);
        var change = roleBindingService.revokeWithResult(
                organizationId, bindingId, actorUserId, clock.instant());
        if (change.changed()) {
            auditLogService.recordOrganizationAdministration(actorUserId, "ORGANIZATION_ROLE_REVOKED",
                    organizationId, change.binding().getId(), requestIdAccessor.current());
        }
        return OrganizationRoleBindingResponse.from(change.binding());
    }

    private void lockOrganization(String organizationId) {
        // Read membership and roles after acquiring the lock so retries see committed changes.
        organizationRepository.findByIdForWrite(organizationId)
                .orElseThrow(() -> new DomainForbiddenException("error.organization.permission.denied"));
    }
}
