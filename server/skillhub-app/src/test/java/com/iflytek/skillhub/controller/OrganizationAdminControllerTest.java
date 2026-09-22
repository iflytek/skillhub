package com.iflytek.skillhub.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.organization.OrganizationRole;
import com.iflytek.skillhub.domain.organization.OrganizationStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.dto.LoginConnectionHealthResponse;
import com.iflytek.skillhub.dto.LoginConnectionResponse;
import com.iflytek.skillhub.dto.LoginConnectionRevisionResponse;
import com.iflytek.skillhub.dto.LoginConnectionSecretSummaryResponse;
import com.iflytek.skillhub.dto.OrganizationDomainChallengeResponse;
import com.iflytek.skillhub.dto.OrganizationResponse;
import com.iflytek.skillhub.service.EnterpriseConnectionAppService;
import com.iflytek.skillhub.service.OrganizationAdminAppService;
import com.iflytek.skillhub.service.PlatformOrganizationAdminAppService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrganizationAdminControllerTest {

    private static final Instant NOW = Instant.parse("2026-09-08T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlatformOrganizationAdminAppService platformAppService;

    @MockBean
    private OrganizationAdminAppService organizationAppService;

    @MockBean
    private EnterpriseConnectionAppService enterpriseConnectionAppService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @Test
    void platformCreationRequiresSuperAdminAndDelegatesAnExplicitOwner() throws Exception {
        given(platformAppService.create(any(), eq("platform-admin")))
                .willReturn(organizationResponse(List.of()));

        mockMvc.perform(post("/api/v1/admin/organizations")
                        .with(authentication(authToken(
                                "platform-admin",
                                "SUPER_ADMIN"
                        )))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "slug": "acme",
                                  "displayName": "Acme",
                                  "initialOwnerUserId": "owner-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.slug").value("acme"));

        verify(platformAppService).create(any(), eq("platform-admin"));
    }

    @Test
    void nonSuperAdminCannotUseThePlatformCreationSurface() throws Exception {
        mockMvc.perform(post("/api/v1/admin/organizations")
                        .with(authentication(authToken("ordinary-user")))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "slug": "acme",
                                  "displayName": "Acme",
                                  "initialOwnerUserId": "owner-1"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void ordinaryAuthenticatedOrganizationAdminCanUseTenantSurface() throws Exception {
        given(organizationAppService.get("organization-a", "identity-admin"))
                .willReturn(organizationResponse(List.of(OrganizationRole.IDENTITY_ADMIN)));

        mockMvc.perform(get("/api/v1/organizations/organization-a")
                        .with(authentication(authToken("identity-admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.callerRoles[0]").value("IDENTITY_ADMIN"));

        verify(organizationAppService).get("organization-a", "identity-admin");
    }

    @Test
    void platformRoleDoesNotBypassOrganizationAuthorization() throws Exception {
        given(organizationAppService.get("organization-a", "platform-admin"))
                .willThrow(new DomainForbiddenException("error.organization.permission.denied"));

        mockMvc.perform(get("/api/v1/organizations/organization-a")
                        .with(authentication(authToken(
                                "platform-admin",
                                "SUPER_ADMIN"
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void anonymousTenantRequestIsRejectedBeforeControllerInvocation() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/organization-a"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void domainChallengeReturnsOneTimeDnsInstructions() throws Exception {
        given(organizationAppService.issueDomainChallenge(
                eq("organization-a"),
                any(),
                eq("identity-admin")
        )).willReturn(new OrganizationDomainChallengeResponse(
                "domain-1",
                "example.com",
                com.iflytek.skillhub.domain.organization.OrganizationDomainVerificationMethod.DNS_TXT,
                "_skillhub-verification.example.com",
                "skillhub-verification=one-time-token"
        ));

        mockMvc.perform(post("/api/v1/organizations/organization-a/domains")
                        .with(authentication(authToken("identity-admin")))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"domain": "example.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordName")
                        .value("_skillhub-verification.example.com"))
                .andExpect(jsonPath("$.data.recordValue")
                        .value("skillhub-verification=one-time-token"));
    }

    @Test
    void loginConnectionCreationNeverEchoesTheSubmittedSecret() throws Exception {
        given(enterpriseConnectionAppService.create(
                eq("organization-a"),
                any(),
                eq("identity-admin")
        )).willReturn(new LoginConnectionResponse(
                "connection-1",
                "login-safe-handle",
                "Corporate SSO",
                "oidc",
                "DRAFT",
                null,
                null,
                new LoginConnectionRevisionResponse(
                        "revision-1",
                        1,
                        "1.0",
                        1,
                        Map.of(
                                "issuer", "https://id.example.com",
                                "clientId", "skillhub",
                                "scopes", List.of("openid")
                        ),
                        NOW
                ),
                new LoginConnectionSecretSummaryResponse(true, NOW, null),
                new LoginConnectionHealthResponse("UNTESTED", null, null),
                NOW,
                NOW
        ));

        String body = mockMvc.perform(post(
                        "/api/v1/organizations/organization-a/login-connections"
                )
                        .with(authentication(authToken("identity-admin")))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "displayName": "Corporate SSO",
                                  "adapterKey": "oidc",
                                  "configuration": {
                                    "issuer": "https://id.example.com",
                                  "clientId": "skillhub",
                                  "scopes": ["openid"]
                                  },
                                  "clientSecret": "never-echo-this-secret"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.secret.configured").value(true))
                .andExpect(jsonPath("$.data.latestRevision.configuration.clientId")
                        .value("skillhub"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("never-echo-this-secret")
                .doesNotContain("clientSecret");
    }

    @Test
    void activationRequiresExplicitConfirmationBeforeCallingTheService() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/organizations/organization-a/login-connections/connection-1"
                                + "/revisions/revision-1/activate"
                )
                        .with(authentication(authToken("identity-admin")))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"confirmed\":false}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(enterpriseConnectionAppService);
    }

    private OrganizationResponse organizationResponse(List<OrganizationRole> roles) {
        return new OrganizationResponse(
                "organization-a",
                "acme",
                "Acme",
                OrganizationStatus.ACTIVE,
                1,
                "platform-admin",
                NOW,
                NOW,
                roles
        );
    }

    private UsernamePasswordAuthenticationToken authToken(
            String userId,
            String... roles
    ) {
        Set<String> platformRoles = Set.of(roles);
        PlatformPrincipal principal = new PlatformPrincipal(
                userId,
                userId,
                userId + "@example.com",
                null,
                "github",
                platformRoles
        );
        List<SimpleGrantedAuthority> authorities = platformRoles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }
}
