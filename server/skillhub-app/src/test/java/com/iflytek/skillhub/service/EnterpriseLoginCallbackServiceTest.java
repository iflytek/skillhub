package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.connection.oidc.OidcConnectionConfiguration;
import com.iflytek.skillhub.auth.connection.oidc.OidcLoginProtocolClient;
import com.iflytek.skillhub.auth.connection.persistence.LoginConnection;
import com.iflytek.skillhub.auth.connection.persistence.LoginConnectionRepository;
import com.iflytek.skillhub.auth.connection.persistence.LoginConnectionRevision;
import com.iflytek.skillhub.auth.connection.persistence.LoginConnectionRevisionRepository;
import com.iflytek.skillhub.auth.connection.persistence.LoginConnectionStatus;
import com.iflytek.skillhub.auth.connection.secret.LoginConnectionSecretReader;
import com.iflytek.skillhub.auth.federation.association.EnterpriseIdentityAssociation;
import com.iflytek.skillhub.auth.federation.association.EnterpriseIdentityAssociationService;
import com.iflytek.skillhub.auth.federation.config.EnterpriseIdentityRolloutProperties;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.auth.session.IdentitySessionOriginService;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
import com.iflytek.skillhub.domain.organization.Organization;
import com.iflytek.skillhub.domain.organization.OrganizationMembership;
import com.iflytek.skillhub.domain.organization.OrganizationMembershipRepository;
import com.iflytek.skillhub.domain.organization.OrganizationMembershipStatus;
import com.iflytek.skillhub.domain.organization.OrganizationRepository;
import com.iflytek.skillhub.domain.organization.OrganizationStatus;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.auth.federation.core.IdentityCorrelationStage;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;

class EnterpriseLoginCallbackServiceTest {
    private final EnterpriseLoginStartService transactions = mock(EnterpriseLoginStartService.class);
    private final LoginConnectionRepository connections = mock(LoginConnectionRepository.class);
    private final LoginConnectionRevisionRepository revisions = mock(LoginConnectionRevisionRepository.class);
    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final OrganizationMembershipRepository memberships = mock(OrganizationMembershipRepository.class);
    private final LoginConnectionSecretReader secrets = mock(LoginConnectionSecretReader.class);
    private final OidcLoginProtocolClient oidc = mock(OidcLoginProtocolClient.class);
    private final EnterpriseIdentityAssociationService associations = mock(EnterpriseIdentityAssociationService.class);
    private final ObjectProvider<EnterpriseIdentityAssociationService> associationProvider = mock(ObjectProvider.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final UserRoleBindingRepository roles = mock(UserRoleBindingRepository.class);
    private final PlatformSessionService sessions = mock(PlatformSessionService.class);
    private final IdentitySessionOriginService origins = mock(IdentitySessionOriginService.class);
    private final EnterpriseIdentityRolloutProperties rollout = new EnterpriseIdentityRolloutProperties();
    private final LoginConnection connection = mock(LoginConnection.class);
    private final LoginConnectionRevision revision = mock(LoginConnectionRevision.class);
    private final Organization organization = mock(Organization.class);
    private final OrganizationMembership membership = mock(OrganizationMembership.class);
    private EnterpriseLoginCallbackService service;
    private EnterpriseLoginStartService.EnterpriseLoginTransaction transaction;

    @BeforeEach
    void setUp() throws Exception {
        rollout.setOidcLoginEnabled(true);
        rollout.setOidcCallbackBaseUri("https://skillhub.example.test");
        rollout.setOrganizationAllowlist(java.util.Set.of("org-1"));
        transaction = new EnterpriseLoginStartService.EnterpriseLoginTransaction(
                "state-1234567890123456789012345678901234567890123", "handle-1", "connection-1",
                "org-1", "revision-1", "nonce-1", "verifier-1", "binding-1234567890123456789012345678901234567890123",
                "https://skillhub.example.test/api/v1/auth/enterprise/handle-1/callback", "/dashboard");
        when(transactions.consume(anyString(), anyString())).thenReturn(transaction);
        when(connections.findByPublicHandle("handle-1")).thenReturn(Optional.of(connection));
        when(connection.getId()).thenReturn("connection-1");
        when(connection.getOrganizationId()).thenReturn("org-1");
        when(connection.getStatus()).thenReturn(LoginConnectionStatus.ACTIVE);
        when(connection.getActiveRevisionId()).thenReturn("revision-1");
        when(revisions.findById("revision-1")).thenReturn(Optional.of(revision));
        when(revision.getConfiguration()).thenReturn(new OidcConnectionConfiguration("https://idp.example.test", "client-1"));
        when(revision.getSecretBindingVersion()).thenReturn(1L);
        when(organizations.findById("org-1")).thenReturn(Optional.of(organization));
        when(organization.getId()).thenReturn("org-1");
        when(organization.getStatus()).thenReturn(OrganizationStatus.ACTIVE);
        when(memberships.findByOrganizationIdAndId("org-1", "membership-1")).thenReturn(Optional.of(membership));
        when(membership.getId()).thenReturn("membership-1");
        when(membership.getUserId()).thenReturn("user-1");
        when(membership.getStatus()).thenReturn(OrganizationMembershipStatus.ACTIVE);
        when(secrets.read("org-1", "connection-1", 1L)).thenReturn(Optional.of("client-secret"));
        when(oidc.metadata(any())).thenReturn(new OidcLoginProtocolClient.ProviderMetadata(
                URI.create("https://idp.example.test/authorize"), URI.create("https://idp.example.test/token"),
                URI.create("https://idp.example.test/jwks"), null));
        when(oidc.exchangeCode(any(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new OidcLoginProtocolClient.TokenResponse("access-token", "id-token"));
        when(oidc.verifyIdToken(anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(new JWTClaimsSet.Builder().issuer("https://idp.example.test").subject("subject-1")
                        .audience("client-1").expirationTime(java.util.Date.from(Instant.now().plusSeconds(300)))
                        .claim("nonce", "nonce-1").build());
        when(oidc.userInfo(any(), anyString())).thenReturn(JsonNodeFactory.instance.objectNode());
        when(associationProvider.getIfAvailable(ArgumentMatchers.any())).thenReturn(associations);
        when(associations.associate(any(), any())).thenReturn(
                new EnterpriseIdentityAssociation("external-1", "user-1", "membership-1",
                        IdentityCorrelationStage.EXACT_BINDING, false));
        UserAccount account = new UserAccount("user-1", "User", "user@example.com", null);
        when(accounts.findById("user-1")).thenReturn(Optional.of(account));
        service = new EnterpriseLoginCallbackService(transactions, connections, revisions, organizations,
                memberships, rollout, secrets, oidc, associationProvider, accounts, roles, sessions, origins);
    }

    @Test
    void validCallbackAssociatesIdentityAndRecordsSessionOrigin() {
        HttpServletRequest request = new MockHttpServletRequest();

        service.complete("handle-1", transaction.state(), "code-1", transaction.browserBinding(), request);

        verify(associations).associate(any(), any());
        verify(sessions).establishSession(any(PlatformPrincipal.class), any(HttpServletRequest.class), ArgumentMatchers.eq(true));
        verify(origins).record(anyString(), ArgumentMatchers.eq("user-1"), ArgumentMatchers.eq(organization),
                ArgumentMatchers.eq(membership), ArgumentMatchers.eq("connection-1"), any(EnterpriseIdentityAssociation.class));
    }

    @Test
    void callbackRejectsWhenConnectionRevisionWasRotated() {
        when(connection.getActiveRevisionId()).thenReturn("revision-2");

        assertThatThrownBy(() -> service.complete("handle-1", transaction.state(), "code-1",
                transaction.browserBinding(), new MockHttpServletRequest()))
                .isInstanceOf(IllegalStateException.class);
        verify(oidc, never()).exchangeCode(any(), anyString(), anyString(), anyString(), anyString(), anyString());
    }
}
