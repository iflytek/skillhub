package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.iflytek.skillhub.auth.federation.adapter.RemoteIdentityIoExecutor;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.audit.OrganizationAuditAction;
import com.iflytek.skillhub.domain.audit.OrganizationAuditEvent;
import com.iflytek.skillhub.domain.audit.OrganizationAuditResult;
import com.iflytek.skillhub.domain.organization.MembershipSourceType;
import com.iflytek.skillhub.domain.organization.Organization;
import com.iflytek.skillhub.domain.organization.OrganizationAdministrativeAction;
import com.iflytek.skillhub.domain.organization.OrganizationAuthorizationService;
import com.iflytek.skillhub.domain.organization.OrganizationDomain;
import com.iflytek.skillhub.domain.organization.OrganizationDomainChallenge;
import com.iflytek.skillhub.domain.organization.OrganizationDomainProofResolver;
import com.iflytek.skillhub.domain.organization.OrganizationDomainRepository;
import com.iflytek.skillhub.domain.organization.OrganizationDomainService;
import com.iflytek.skillhub.domain.organization.OrganizationDomainVerificationMethod;
import com.iflytek.skillhub.domain.organization.OrganizationMembershipService;
import com.iflytek.skillhub.domain.organization.OrganizationMembership;
import com.iflytek.skillhub.domain.organization.OrganizationRepository;
import com.iflytek.skillhub.domain.organization.OrganizationRole;
import com.iflytek.skillhub.domain.organization.OrganizationRoleBinding;
import com.iflytek.skillhub.domain.organization.OrganizationRoleBindingService;
import com.iflytek.skillhub.dto.OrganizationDomainChallengeResponse;
import com.iflytek.skillhub.dto.OrganizationDomainCreateRequest;
import com.iflytek.skillhub.dto.OrganizationResponse;
import com.iflytek.skillhub.dto.OrganizationRoleBindingCreateRequest;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.repository.EnterpriseIdentityQueryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganizationAdminAppServiceTest {

    private static final String ORGANIZATION_ID = "organization-a";
    private static final String ACTOR_ID = "identity-admin";
    private static final Instant NOW = Instant.parse("2026-09-08T10:00:00Z");

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationDomainRepository domainRepository;

    @Mock
    private OrganizationAuthorizationService authorizationService;

    @Mock
    private OrganizationDomainService domainService;

    @Mock
    private OrganizationDomainProofResolver proofResolver;

    @Mock
    private RemoteIdentityIoExecutor remoteIdentityIo;

    @Mock
    private OrganizationMembershipService membershipService;

    @Mock
    private OrganizationRoleBindingService roleBindingService;

    @Mock
    private EnterpriseIdentityQueryRepository queryRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private RequestIdAccessor requestIdAccessor;

    private OrganizationAdminAppService service;

    @BeforeEach
    void setUp() {
        service = new OrganizationAdminAppService(
                organizationRepository,
                domainRepository,
                authorizationService,
                domainService,
                proofResolver,
                remoteIdentityIo,
                membershipService,
                roleBindingService,
                queryRepository,
                auditLogService,
                requestIdAccessor,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void detailUsesOrganizationRolesAndNeverPlatformRoles() {
        Organization organization = organization();
        given(organizationRepository.findById(ORGANIZATION_ID))
                .willReturn(Optional.of(organization));
        given(queryRepository.findActiveRoles(List.of(ORGANIZATION_ID), ACTOR_ID))
                .willReturn(Map.of(
                        ORGANIZATION_ID,
                        Set.of(OrganizationRole.IDENTITY_ADMIN)
                ));

        OrganizationResponse result = service.get(ORGANIZATION_ID, ACTOR_ID);

        verify(authorizationService).requireAllowed(
                ORGANIZATION_ID,
                ACTOR_ID,
                OrganizationAdministrativeAction.VIEW_ORGANIZATION
        );
        assertThat(result.callerRoles()).containsExactly(OrganizationRole.IDENTITY_ADMIN);
    }

    @Test
    void challengeReturnsTheExactDnsRecordWithoutPersistingPresentationSyntax() {
        given(requestIdAccessor.current()).willReturn("request-1");
        given(domainService.issueChallenge(
                ORGANIZATION_ID,
                "Example.COM",
                OrganizationDomainVerificationMethod.DNS_TXT,
                ACTOR_ID,
                NOW
        )).willReturn(new OrganizationDomainChallenge(
                "domain-1",
                "example.com",
                OrganizationDomainVerificationMethod.DNS_TXT,
                "one-time-token"
        ));

        OrganizationDomainChallengeResponse result = service.issueDomainChallenge(
                ORGANIZATION_ID,
                new OrganizationDomainCreateRequest("Example.COM"),
                ACTOR_ID
        );

        assertThat(result.recordName()).isEqualTo("_skillhub-verification.example.com");
        assertThat(result.recordValue())
                .isEqualTo("skillhub-verification=one-time-token");
        assertThat(result.toString()).doesNotContain("one-time-token");

        ArgumentCaptor<OrganizationAuditEvent> eventCaptor =
                ArgumentCaptor.forClass(OrganizationAuditEvent.class);
        verify(auditLogService).record(eventCaptor.capture());
        assertThat(eventCaptor.getValue().action())
                .isEqualTo(OrganizationAuditAction.DOMAIN_CHALLENGE_ISSUED);
        assertThat(eventCaptor.getValue().requestId()).isEqualTo("request-1");
        assertThat(eventCaptor.getValue().detail().toJson())
                .doesNotContain("one-time-token", "skillhub-verification");
    }

    @Test
    void verifyReadsDnsBeforeEnteringTheTransactionalDomainMutation() {
        given(requestIdAccessor.current()).willReturn("request-1");
        OrganizationDomain claim = OrganizationDomain.claim(
                ORGANIZATION_ID,
                "example.com",
                OrganizationDomainVerificationMethod.DNS_TXT,
                "digest-not-exposed-to-the-app-layer",
                NOW.minusSeconds(60)
        );
        given(domainRepository.findByOrganizationIdAndId(ORGANIZATION_ID, claim.getId()))
                .willReturn(Optional.of(claim));
        given(proofResolver.resolveTxt("_skillhub-verification.example.com"))
                .willReturn(List.of(
                        "unrelated=value",
                        "skillhub-verification=current-token"
                ));
        doAnswer(invocation -> invocation.<java.util.function.Supplier<List<String>>>getArgument(0)
                        .get())
                .when(remoteIdentityIo)
                .execute(org.mockito.ArgumentMatchers.any());
        given(domainService.verifyAny(
                ORGANIZATION_ID,
                claim.getId(),
                List.of("current-token"),
                ACTOR_ID,
                NOW
        )).willAnswer(invocation -> {
            claim.verify(NOW);
            return claim;
        });

        service.verifyDomain(ORGANIZATION_ID, claim.getId(), ACTOR_ID);

        InOrder order = inOrder(
                authorizationService,
                remoteIdentityIo,
                proofResolver,
                domainService
        );
        order.verify(authorizationService).requireAllowed(
                ORGANIZATION_ID,
                ACTOR_ID,
                OrganizationAdministrativeAction.MANAGE_DOMAINS
        );
        order.verify(remoteIdentityIo).execute(org.mockito.ArgumentMatchers.any());
        order.verify(proofResolver).resolveTxt("_skillhub-verification.example.com");
        order.verify(domainService).verifyAny(
                ORGANIZATION_ID,
                claim.getId(),
                List.of("current-token"),
                ACTOR_ID,
                NOW
        );

        ArgumentCaptor<OrganizationAuditEvent> eventCaptor =
                ArgumentCaptor.forClass(OrganizationAuditEvent.class);
        verify(auditLogService).record(eventCaptor.capture());
        assertThat(eventCaptor.getValue().action())
                .isEqualTo(OrganizationAuditAction.DOMAIN_VERIFIED);
        assertThat(eventCaptor.getValue().detail().toJson())
                .contains("PENDING", "VERIFIED")
                .doesNotContain("current-token", "digest-not-exposed-to-the-app-layer");
    }

    @Test
    void organizationMembershipAndRoleMutationsEmitTenantCorrelatedResults() {
        given(requestIdAccessor.current()).willReturn("request-1");
        Organization organization = organization();
        given(organizationRepository.findById(ORGANIZATION_ID))
                .willReturn(Optional.of(organization));

        OrganizationMembership membership = OrganizationMembership.provisioned(
                ORGANIZATION_ID,
                MembershipSourceType.MANUAL,
                "member-1",
                "Member One",
                "member-1@example.test",
                NOW.minusSeconds(120)
        );
        membership.activate("member-1", NOW.minusSeconds(60));
        membership.suspend(NOW);
        given(membershipService.suspend(
                ORGANIZATION_ID,
                membership.getId(),
                ACTOR_ID,
                NOW
        )).willReturn(membership);

        OrganizationRoleBinding binding = OrganizationRoleBinding.grant(
                ORGANIZATION_ID,
                "member-1",
                OrganizationRole.IDENTITY_ADMIN,
                ACTOR_ID,
                NOW
        );
        given(roleBindingService.grant(
                ORGANIZATION_ID,
                "member-1",
                OrganizationRole.IDENTITY_ADMIN,
                ACTOR_ID,
                NOW
        )).willReturn(binding);

        service.suspend(ORGANIZATION_ID, ACTOR_ID);
        service.suspendMember(ORGANIZATION_ID, membership.getId(), ACTOR_ID);
        service.grantRole(
                ORGANIZATION_ID,
                new OrganizationRoleBindingCreateRequest(
                        "member-1",
                        OrganizationRole.IDENTITY_ADMIN
                ),
                ACTOR_ID
        );

        ArgumentCaptor<OrganizationAuditEvent> eventCaptor =
                ArgumentCaptor.forClass(OrganizationAuditEvent.class);
        verify(auditLogService, times(3)).record(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .extracting(OrganizationAuditEvent::action)
                .containsExactly(
                        OrganizationAuditAction.ORGANIZATION_SUSPENDED,
                        OrganizationAuditAction.MEMBER_SUSPENDED,
                        OrganizationAuditAction.ROLE_GRANTED
                );
        assertThat(eventCaptor.getAllValues())
                .allSatisfy(event -> {
                    assertThat(event.actorUserId()).isEqualTo(ACTOR_ID);
                    assertThat(event.organizationId()).isEqualTo(ORGANIZATION_ID);
                    assertThat(event.result()).isEqualTo(OrganizationAuditResult.SUCCESS);
                    assertThat(event.requestId()).isEqualTo("request-1");
                });
    }

    private Organization organization() {
        return Organization.create(
                "organization-a",
                "Organization A",
                "platform-admin",
                NOW.minusSeconds(120)
        );
    }
}
