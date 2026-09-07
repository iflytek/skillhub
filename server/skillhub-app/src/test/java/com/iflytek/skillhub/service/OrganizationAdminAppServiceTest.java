package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

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
import com.iflytek.skillhub.domain.organization.OrganizationMembershipService;
import com.iflytek.skillhub.domain.organization.OrganizationRepository;
import com.iflytek.skillhub.domain.organization.OrganizationRole;
import com.iflytek.skillhub.domain.organization.OrganizationRoleBindingService;
import com.iflytek.skillhub.dto.OrganizationDomainChallengeResponse;
import com.iflytek.skillhub.dto.OrganizationDomainCreateRequest;
import com.iflytek.skillhub.dto.OrganizationResponse;
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
    }

    @Test
    void verifyReadsDnsBeforeEnteringTheTransactionalDomainMutation() {
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
        )).willReturn(claim);

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
