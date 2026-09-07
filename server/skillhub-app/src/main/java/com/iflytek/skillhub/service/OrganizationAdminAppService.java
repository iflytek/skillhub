package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.federation.adapter.RemoteIdentityIoExecutor;
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
        return new OrganizationDomainChallengeResponse(
                challenge.domainId(),
                challenge.domain(),
                challenge.method(),
                dnsRecordName(challenge.domain()),
                DNS_VALUE_PREFIX + challenge.proofToken()
        );
    }

    /** Performs remote DNS I/O before calling the transactional domain mutation. */
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
        List<String> candidates = remoteIdentityIo.execute(() ->
                proofResolver.resolveTxt(dnsRecordName(claim.getDomain())))
                .stream()
                .filter(value -> value.startsWith(DNS_VALUE_PREFIX))
                .map(value -> value.substring(DNS_VALUE_PREFIX.length()))
                .filter(value -> !value.isBlank())
                .toList();
        return OrganizationDomainResponse.from(domainService.verifyAny(
                organizationId,
                domainId,
                candidates,
                actorUserId,
                clock.instant()
        ));
    }

    public OrganizationDomainResponse disableDomain(
            String organizationId,
            String domainId,
            String actorUserId
    ) {
        return OrganizationDomainResponse.from(domainService.disable(
                organizationId,
                domainId,
                actorUserId,
                clock.instant()
        ));
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

    public OrganizationMemberResponse addMember(
            String organizationId,
            OrganizationMemberCreateRequest request,
            String actorUserId
    ) {
        return OrganizationMemberResponse.from(membershipService.addManualMember(
                organizationId,
                request.userId(),
                actorUserId,
                clock.instant()
        ));
    }

    public OrganizationMemberResponse suspendMember(
            String organizationId,
            String membershipId,
            String actorUserId
    ) {
        return OrganizationMemberResponse.from(membershipService.suspend(
                organizationId,
                membershipId,
                actorUserId,
                clock.instant()
        ));
    }

    public OrganizationMemberResponse reactivateMember(
            String organizationId,
            String membershipId,
            String actorUserId
    ) {
        return OrganizationMemberResponse.from(membershipService.reactivate(
                organizationId,
                membershipId,
                actorUserId,
                clock.instant()
        ));
    }

    public OrganizationMemberResponse deprovisionMember(
            String organizationId,
            String membershipId,
            String actorUserId
    ) {
        return OrganizationMemberResponse.from(membershipService.deprovision(
                organizationId,
                membershipId,
                actorUserId,
                clock.instant()
        ));
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
        return OrganizationRoleBindingResponse.from(binding);
    }

    public OrganizationRoleBindingResponse revokeRole(
            String organizationId,
            String bindingId,
            String actorUserId
    ) {
        return OrganizationRoleBindingResponse.from(roleBindingService.revoke(
                organizationId,
                bindingId,
                actorUserId,
                clock.instant()
        ));
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
        Instant now = clock.instant();
        switch (transition) {
            case SUSPEND -> organization.suspend(now);
            case REACTIVATE -> organization.reactivate(now);
            case DECOMMISSION -> organization.decommission(now);
        }
        organizationRepository.save(organization);
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

    private enum OrganizationTransition {
        SUSPEND,
        REACTIVATE,
        DECOMMISSION
    }
}
