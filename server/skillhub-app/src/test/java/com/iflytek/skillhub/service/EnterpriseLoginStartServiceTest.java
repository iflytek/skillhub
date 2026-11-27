package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import com.iflytek.skillhub.auth.federation.config.EnterpriseIdentityRolloutProperties;
import com.iflytek.skillhub.domain.organization.Organization;
import com.iflytek.skillhub.domain.organization.OrganizationRepository;
import com.iflytek.skillhub.domain.organization.OrganizationStatus;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

class EnterpriseLoginStartServiceTest {
    private final LoginConnectionRepository connections = mock(LoginConnectionRepository.class);
    private final LoginConnectionRevisionRepository revisions = mock(LoginConnectionRevisionRepository.class);
    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final OidcLoginProtocolClient oidc = mock(OidcLoginProtocolClient.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final EnterpriseIdentityRolloutProperties rollout = new EnterpriseIdentityRolloutProperties();
    private EnterpriseLoginStartService service;
    private LoginConnection connection;
    private LoginConnectionRevision revision;
    private OidcConnectionConfiguration configuration;

    @BeforeEach
    void setUp() {
        rollout.setOidcLoginEnabled(true);
        rollout.setOidcCallbackBaseUri("https://skillhub.example.test");
        rollout.setOrganizationAllowlist(java.util.Set.of("org-1"));
        connection = mock(LoginConnection.class);
        revision = mock(LoginConnectionRevision.class);
        configuration = new OidcConnectionConfiguration("https://idp.example.test", "client-1");
        when(connections.findByPublicHandle("conn-public")).thenReturn(Optional.of(connection));
        when(connection.getStatus()).thenReturn(LoginConnectionStatus.ACTIVE);
        when(connection.getPublicHandle()).thenReturn("conn-public");
        when(connection.getId()).thenReturn("conn-1");
        when(connection.getOrganizationId()).thenReturn("org-1");
        when(connection.getActiveRevisionId()).thenReturn("rev-1");
        when(revisions.findById("rev-1")).thenReturn(Optional.of(revision));
        when(revision.getId()).thenReturn("rev-1");
        when(revision.getConfiguration()).thenReturn(configuration);
        when(revision.getSecretBindingVersion()).thenReturn(1L);
        Organization organization = mock(Organization.class);
        when(organization.getStatus()).thenReturn(OrganizationStatus.ACTIVE);
        when(organizations.findById("org-1")).thenReturn(Optional.of(organization));
        when(oidc.metadata(configuration)).thenReturn(new OidcLoginProtocolClient.ProviderMetadata(
                URI.create("https://idp.example.test/authorize"),
                URI.create("https://idp.example.test/token"),
                URI.create("https://idp.example.test/jwks"), null));
        when(redis.opsForValue()).thenReturn(values);
        service = new EnterpriseLoginStartService(connections, revisions, organizations, rollout,
                oidc, new ObjectMapper(), redis);
    }

    @Test
    void startCreatesBoundTransactionAndPkceAuthorization() {
        EnterpriseLoginStartService.StartResult result = service.start("conn-public", "/dashboard?tab=members");

        assertThat(result.authorizationUri().toString())
                .startsWith("https://idp.example.test/authorize?")
                .contains("response_type=code")
                .contains("client_id=client-1")
                .contains("redirect_uri=https%3A%2F%2Fskillhub.example.test%2Fapi%2Fv1%2Fauth%2Fenterprise%2Fconn-public%2Fcallback")
                .contains("code_challenge_method=S256")
                .contains("state=" ).contains("nonce=").contains("code_challenge=");
        assertThat(result.transaction().returnTo()).isEqualTo("/dashboard?tab=members");
        assertThat(result.transaction().browserBinding()).isEqualTo(result.browserBinding());
        verify(values).set(any(), any(), eq(300L), any());
    }

    @Test
    void startDropsExternalReturnTo() {
        EnterpriseLoginStartService.StartResult result = service.start("conn-public", "https://evil.example");

        assertThat(result.transaction().returnTo()).isEqualTo("/");
    }

    @Test
    void disabledRolloutDoesNotReadConnection() {
        rollout.setOidcLoginEnabled(false);

        assertThatThrownBy(() -> service.start("conn-public", "/"))
                .isInstanceOf(IllegalStateException.class);
        org.mockito.Mockito.verifyNoInteractions(connections);
    }

    @Test
    void consumeRejectsMissingOrMismatchedBrowserBinding() {
        when(redis.execute(any(DefaultRedisScript.class), anyList(), eq("wrong"))).thenReturn("__BINDING_MISMATCH__");

        assertThatThrownBy(() -> service.consume("state-1", "wrong"))
                .isInstanceOf(IllegalStateException.class);
    }
}
