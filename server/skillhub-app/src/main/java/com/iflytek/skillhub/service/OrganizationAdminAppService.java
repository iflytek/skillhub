package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.federation.adapter.RemoteIdentityIoExecutor;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.audit.OrganizationAuditAction;
import com.iflytek.skillhub.domain.audit.OrganizationAuditDetail;
import com.iflytek.skillhub.domain.audit.OrganizationAuditEvent;
import com.iflytek.skillhub.domain.audit.OrganizationAuditTargetType;
import com.iflytek.skillhub.domain.organization.Organization;
import com.iflytek.skillhub.domain.organization.OrganizationAdministrativeAction;
import com.iflytek.skillhub.domain.organization.OrganizationAuthorizationService;
import com.iflytek.skillhub.domain.organization.OrganizationDomain;
import com.iflytek.skillhub.domain.organization.OrganizationDomainChallenge;
import com.iflytek.skillhub.domain.organization.OrganizationDomainProofResolver;
import com.iflytek.skillhub.domain.organization.OrganizationDomainRepository;
import com.iflytek.skillhub.domain.organization.OrganizationDomainService;
import com.iflytek.skillhub.domain.organization.OrganizationDomainVerificationMethod;
import com.iflytek.skillhub.domain.organization.OrganizationMembership;
import com.iflytek.skillhub.domain.organization.OrganizationMembershipService;
import com.iflytek.skillhub.domain.organization.OrganizationRepository;
import com.iflytek.skillhub.domain.organization.OrganizationRole;
import com.iflytek.skillhub.domain.organization.OrganizationRoleBinding;
import com.iflytek.skillhub.domain.organization.OrganizationRoleBindingService;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.OrganizationDomainChallengeResponse;
import com.iflytek.skillhub.dto.OrganizationDomainCreateRequest;
import com.iflytek.skillhub.dto.OrganizationDomainResponse;
import com.iflytek.skillhub.dto.OrganizationMemberCreateRequest;
import com.iflytek.skillhub.dto.OrganizationMemberResponse;
import com.iflytek.skillhub.dto.OrganizationResponse;
import com.iflytek.skillhub.dto.OrganizationRoleBindingCreateRequest;
import com.iflytek.skillhub.dto.OrganizationRoleBindingResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.repository.EnterpriseIdentityQueryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Organization-scoped administration; every tenant operation delegates to Organization RBAC. */
@Service
public class OrganizationAdminAppService {

    static final String DNS_RECORD_PREFIX = "_skillhub-verification.";
    static final String DNS_VALUE_PREFIX = "skillhub-verification=";
    private static final int MAX_PAGE_SIZE = 100;

    private final OrganizationRepository organizationRepository;
    private final OrganizationDomainRepository domainRepository;
    private final OrganizationAuthorizationService authorizationService;
    private final OrganizationDomainService domainService;
    private final OrganizationDomainProofResolver proofResolver;
    private final RemoteIdentityIoExecutor remoteIdentityIo;
    private final OrganizationMembershipService membershipService;
    private final OrganizationRoleBindingService roleBindingService;
    private final EnterpriseIdentityQueryRepository queryRepository;
    private final AuditLogService auditLogService;
    private final RequestIdAccessor requestIdAccessor;
    private final Clock clock;

    public OrganizationAdminAppService(
            OrganizationRepository organizationRepository,
            OrganizationDomainRepository domainRepository,
            OrganizationAuthorizationService authorizationService,
            OrganizationDomainService domainService,
            OrganizationDomainProofResolver proofResolver,
            RemoteIdentityIoExecutor remoteIdentityIo,
            OrganizationMembershipService membershipService,
            OrganizationRoleBindingService roleBindingService,
            EnterpriseIdentityQueryRepository queryRepository,
            AuditLogService auditLogService,
            RequestIdAccessor requestIdAccessor,
            Clock clock
    ) {
        this.organizationRepository = organizationRepository;
        this.domainRepository = domainRepository;
        this.authorizationService = authorizationService;
        this.domainService = domainService;
        this.proofResolver = proofResolver;
        this.remoteIdentityIo = remoteIdentityIo;
        this.membershipService = membershipService;
        this.roleBindingService = roleBindingService;
        this.queryRepository = queryRepository;
        this.auditLogService = auditLogService;
        this.requestIdAccessor = requestIdAccessor;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrganizationResponse> listMine(String actorUserId, int page, int size) {
        Page<Organization> organizations = queryRepository.findActiveOrganizationsByUserId(
                actorUserId,
                pageRequest(page, size)
        );
        List<String> organizationIds = organizations.getContent().stream()
                .map(Organization::getId)
                .toList();
        Map<String, Set<OrganizationRole>> roles = queryRepository.findActiveRoles(
                organizationIds,
                actorUserId
        );
        return PageResponse.from(organizations.map(organization -> OrganizationResponse.from(
                organization,
                roles.getOrDefault(organization.getId(), Set.of())
        )));
    }

    @Transactional(readOnly = true)
    public OrganizationResponse get(String organizationId, String actorUserId) {
        authorize(organizationId, actorUserId, OrganizationAdministrativeAction.VIEW_ORGANIZATION);
        Organization organization = requireOrganization(organizationId);
        Set<OrganizationRole> roles = queryRepository.findActiveRoles(
                List.of(organizationId),
                actorUserId
        ).getOrDefault(organizationId, Set.of());
        return OrganizationResponse.from(organization, roles);
    }

    @Transactional
    public OrganizationResponse suspend(String organizationId, String actorUserId) {
        return transition(organizationId, actorUserId, OrganizationTransition.SUSPEND);
    }

    @Transactional
    public OrganizationResponse reactivate(String organizationId, String actorUserId) {
        return transition(organizationId, actorUserId, OrganizationTransition.REACTIVATE);
    }

    @Transactional
    public OrganizationResponse decommission(String organizationId, String actorUserId) {
        return transition(organizationId, actorUserId, OrganizationTransition.DECOMMISSION);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrganizationDomainResponse> listDomains(
            String organizationId,
            String actorUserId,
            int page,
            int size
    ) {
        authorize(organizationId, actorUserId, OrganizationAdministrativeAction.VIEW_DOMAINS);
        return PageResponse.from(queryRepository.findDomains(
                organizationId,
                pageRequest(page, size)
        ).map(OrganizationDomainResponse::from));
    }

    @Transactional
    public OrganizationDomainChallengeResponse issueDomainChallenge(
            String organizationId,
            OrganizationDomainCreateRequest request,
            String actorUserId
    ) {
        OrganizationDomainChallenge challenge = domainService.issueChallenge(
                organizationId,
                request.domain(),
                OrganizationDomainVerificationMethod.DNS_TXT,
                actorUserId,
                clock.instant()
        );
        recordAudit(
                organizationId,
                actorUserId,
                OrganizationAuditAction.DOMAIN_CHALLENGE_ISSUED,
                OrganizationAuditTargetType.DOMAIN,
                challenge.domainId(),
                OrganizationAuditDetail.transition(null, "PENDING")
        );
        return new OrganizationDomainChallengeResponse(
                challenge.domainId(),
                challenge.domain(),
                challenge.method(),
                dnsRecordName(challenge.domain()),
                DNS_VALUE_PREFIX + challenge.proofToken()
        );
    }

    /** Performs remote DNS I/O before calling the transactional domain mutation. */
    @Transactional
    public OrganizationDomainResponse verifyDomain(
            String organizationId,
            String domainId,
            String actorUserId
    ) {
        authorize(organizationId, actorUserId, OrganizationAdministrativeAction.MANAGE_DOMAINS);
        OrganizationDomain claim = domainRepository.findByOrganizationIdAndId(
                organizationId,
                domainId
        ).orElseThrow(() -> new DomainNotFoundException(
                "error.organization.domain.not-found"
        ));
        String previousStatus = claim.getStatus().name();
        List<String> candidates = remoteIdentityIo.execute(() ->
                proofResolver.resolveTxt(dnsRecordName(claim.getDomain())))
                .stream()
                .filter(value -> value.startsWith(DNS_VALUE_PREFIX))
                .map(value -> value.substring(DNS_VALUE_PREFIX.length()))
                .filter(value -> !value.isBlank())
                .toList();
        OrganizationDomain verified = domainService.verifyAny(
                organizationId,
                domainId,
                candidates,
                actorUserId,
                clock.instant()
        );
        recordAudit(
                organizationId,
                actorUserId,
                OrganizationAuditAction.DOMAIN_VERIFIED,
                OrganizationAuditTargetType.DOMAIN,
                verified.getId(),
                OrganizationAuditDetail.transition(
                        previousStatus,
                        verified.getStatus().name()
                )
        );
        return OrganizationDomainResponse.from(verified);
    }

    @Transactional
    public OrganizationDomainResponse disableDomain(
            String organizationId,
            String domainId,
            String actorUserId
    ) {
        OrganizationDomain disabled = domainService.disable(
                organizationId,
                domainId,
                actorUserId,
                clock.instant()
        );
        recordAudit(
                organizationId,
                actorUserId,
                OrganizationAuditAction.DOMAIN_DISABLED,
                OrganizationAuditTargetType.DOMAIN,
                disabled.getId(),
                OrganizationAuditDetail.transition(null, disabled.getStatus().name())
        );
        return OrganizationDomainResponse.from(disabled);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrganizationMemberResponse> listMembers(
            String organizationId,
            String actorUserId,
            int page,
            int size
    ) {
        authorize(organizationId, actorUserId, OrganizationAdministrativeAction.VIEW_MEMBERS);
        return PageResponse.from(queryRepository.findMemberships(
                organizationId,
                pageRequest(page, size)
        ).map(OrganizationMemberResponse::from));
    }

    @Transactional
    public OrganizationMemberResponse addMember(
            String organizationId,
            OrganizationMemberCreateRequest request,
            String actorUserId
    ) {
        OrganizationMembership membership = membershipService.addManualMember(
                organizationId,
                request.userId(),
                actorUserId,
                clock.instant()
        );
        recordMembershipAudit(
                OrganizationAuditAction.MEMBER_ADDED,
                organizationId,
                actorUserId,
                membership
        );
        return OrganizationMemberResponse.from(membership);
    }

    @Transactional
    public OrganizationMemberResponse suspendMember(
            String organizationId,
            String membershipId,
            String actorUserId
    ) {
        OrganizationMembership membership = membershipService.suspend(
                organizationId,
                membershipId,
                actorUserId,
                clock.instant()
        );
        recordMembershipAudit(
                OrganizationAuditAction.MEMBER_SUSPENDED,
                organizationId,
                actorUserId,
                membership
        );
        return OrganizationMemberResponse.from(membership);
    }

    @Transactional
    public OrganizationMemberResponse reactivateMember(
            String organizationId,
            String membershipId,
            String actorUserId
    ) {
        OrganizationMembership membership = membershipService.reactivate(
                organizationId,
                membershipId,
                actorUserId,
                clock.instant()
        );
        recordMembershipAudit(
                OrganizationAuditAction.MEMBER_REACTIVATED,
                organizationId,
                actorUserId,
                membership
        );
        return OrganizationMemberResponse.from(membership);
    }

    @Transactional
    public OrganizationMemberResponse deprovisionMember(
            String organizationId,
            String membershipId,
            String actorUserId
    ) {
        OrganizationMembership membership = membershipService.deprovision(
                organizationId,
                membershipId,
                actorUserId,
                clock.instant()
        );
        recordMembershipAudit(
                OrganizationAuditAction.MEMBER_DEPROVISIONED,
                organizationId,
                actorUserId,
                membership
        );
        return OrganizationMemberResponse.from(membership);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrganizationRoleBindingResponse> listRoleBindings(
            String organizationId,
            String actorUserId,
            int page,
            int size
    ) {
        authorize(
                organizationId,
                actorUserId,
                OrganizationAdministrativeAction.VIEW_ORGANIZATION_ROLES
        );
        return PageResponse.from(queryRepository.findRoleBindings(
                organizationId,
                pageRequest(page, size)
        ).map(OrganizationRoleBindingResponse::from));
    }

    @Transactional
    public OrganizationRoleBindingResponse grantRole(
            String organizationId,
            OrganizationRoleBindingCreateRequest request,
            String actorUserId
    ) {
        OrganizationRoleBinding binding = roleBindingService.grant(
                organizationId,
                request.userId(),
                request.role(),
                actorUserId,
                clock.instant()
        );
        recordRoleAudit(
                OrganizationAuditAction.ROLE_GRANTED,
                organizationId,
                actorUserId,
                binding
        );
        return OrganizationRoleBindingResponse.from(binding);
    }

    @Transactional
    public OrganizationRoleBindingResponse revokeRole(
            String organizationId,
            String bindingId,
            String actorUserId
    ) {
        OrganizationRoleBinding binding = roleBindingService.revoke(
                organizationId,
                bindingId,
                actorUserId,
                clock.instant()
        );
        recordRoleAudit(
                OrganizationAuditAction.ROLE_REVOKED,
                organizationId,
                actorUserId,
                binding
        );
        return OrganizationRoleBindingResponse.from(binding);
    }

    private OrganizationResponse transition(
            String organizationId,
            String actorUserId,
            OrganizationTransition transition
    ) {
        authorize(
                organizationId,
                actorUserId,
                OrganizationAdministrativeAction.MANAGE_ORGANIZATION_LIFECYCLE
        );
        Organization organization = requireOrganization(organizationId);
        String previousStatus = organization.getStatus().name();
        Instant now = clock.instant();
        switch (transition) {
            case SUSPEND -> organization.suspend(now);
            case REACTIVATE -> organization.reactivate(now);
            case DECOMMISSION -> organization.decommission(now);
        }
        organizationRepository.save(organization);
        recordAudit(
                organizationId,
                actorUserId,
                transition.auditAction,
                OrganizationAuditTargetType.ORGANIZATION,
                organization.getId(),
                OrganizationAuditDetail.transition(
                        previousStatus,
                        organization.getStatus().name()
                )
        );
        Set<OrganizationRole> roles = queryRepository.findActiveRoles(
                List.of(organizationId),
                actorUserId
        ).getOrDefault(organizationId, Set.of());
        return OrganizationResponse.from(organization, roles);
    }

    private void authorize(
            String organizationId,
            String actorUserId,
            OrganizationAdministrativeAction action
    ) {
        authorizationService.requireAllowed(organizationId, actorUserId, action);
    }

    private Organization requireOrganization(String organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new DomainNotFoundException("error.organization.not-found"));
    }

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    private String dnsRecordName(String domain) {
        return DNS_RECORD_PREFIX + domain;
    }

    private void recordMembershipAudit(
            OrganizationAuditAction action,
            String organizationId,
            String actorUserId,
            OrganizationMembership membership
    ) {
        recordAudit(
                organizationId,
                actorUserId,
                action,
                OrganizationAuditTargetType.MEMBERSHIP,
                membership.getId(),
                OrganizationAuditDetail.member(
                        membership.getStatus().name(),
                        membership.getSourceType().name(),
                        membership.getUserId()
                )
        );
    }

    private void recordRoleAudit(
            OrganizationAuditAction action,
            String organizationId,
            String actorUserId,
            OrganizationRoleBinding binding
    ) {
        recordAudit(
                organizationId,
                actorUserId,
                action,
                OrganizationAuditTargetType.ROLE_BINDING,
                binding.getId(),
                OrganizationAuditDetail.role(
                        binding.getStatus().name(),
                        binding.getRole().name(),
                        binding.getUserId()
                )
        );
    }

    private void recordAudit(
            String organizationId,
            String actorUserId,
            OrganizationAuditAction action,
            OrganizationAuditTargetType targetType,
            String targetReference,
            OrganizationAuditDetail detail
    ) {
        auditLogService.record(OrganizationAuditEvent.success(
                actorUserId,
                organizationId,
                action,
                targetType,
                targetReference,
                requestIdAccessor.current(),
                detail
        ));
    }

    private enum OrganizationTransition {
        SUSPEND(OrganizationAuditAction.ORGANIZATION_SUSPENDED),
        REACTIVATE(OrganizationAuditAction.ORGANIZATION_REACTIVATED),
        DECOMMISSION(OrganizationAuditAction.ORGANIZATION_DECOMMISSIONED);

        private final OrganizationAuditAction auditAction;

        OrganizationTransition(OrganizationAuditAction auditAction) {
            this.auditAction = auditAction;
        }
    }
}
