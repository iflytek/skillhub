package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.connection.persistence.LoginConnection;
import com.iflytek.skillhub.auth.connection.persistence.LoginConnectionRepository;
import com.iflytek.skillhub.auth.connection.persistence.LoginConnectionStatus;
import com.iflytek.skillhub.auth.federation.config.EnterpriseIdentityRolloutProperties;
import com.iflytek.skillhub.domain.organization.Organization;
import com.iflytek.skillhub.domain.organization.OrganizationRepository;
import com.iflytek.skillhub.domain.organization.OrganizationStatus;
import com.iflytek.skillhub.dto.AuthMethodResponse;
import com.iflytek.skillhub.dto.EnterpriseLoginDiscoveryResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EnterpriseLoginDiscoveryServiceTest {
    @Test
    void onlyReturnsOpaqueHandleForAllowlistedActiveOrganization() {
        OrganizationRepository organizations = mock(OrganizationRepository.class);
        LoginConnectionRepository connections = mock(LoginConnectionRepository.class);
        AuthMethodCatalog methods = mock(AuthMethodCatalog.class);
        EnterpriseIdentityRolloutProperties rollout = new EnterpriseIdentityRolloutProperties();
        rollout.setOidcLoginEnabled(true);
        rollout.setOrganizationAllowlist(java.util.Set.of("org-1"));
        when(methods.listMethods("/dashboard")).thenReturn(List.of(
                new AuthMethodResponse("local", "PASSWORD", "local", "Local", "/local")));
        Organization organization = mock(Organization.class);
        when(organization.getId()).thenReturn("org-1");
        when(organization.getSlug()).thenReturn("acme");
        when(organization.getDisplayName()).thenReturn("Acme");
        when(organization.getStatus()).thenReturn(OrganizationStatus.ACTIVE);
        when(organizations.findBySlug("acme")).thenReturn(Optional.of(organization));
        LoginConnection connection = mock(LoginConnection.class);
        when(connection.getDisplayName()).thenReturn("Acme SSO");
        when(connection.getPublicHandle()).thenReturn("opaque-handle");
        when(connection.getActiveRevisionId()).thenReturn("revision-1");
        when(connections.findByOrganizationIdAndStatusOrderByDisplayNameAsc("org-1", LoginConnectionStatus.ACTIVE))
                .thenReturn(List.of(connection));

        EnterpriseLoginDiscoveryResponse response = new EnterpriseLoginDiscoveryService(
                organizations, connections, methods, rollout).discover("ACME", "/dashboard");

        assertThat(response.publicMethods()).extracting(AuthMethodResponse::methodType)
                .containsExactly("PASSWORD");
        assertThat(response.organizations()).singleElement().satisfies(options -> {
            assertThat(options.slug()).isEqualTo("acme");
            assertThat(options.loginOptions()).singleElement().satisfies(option -> {
                assertThat(option.actionUrl()).isEqualTo(
                        "/api/v1/auth/enterprise/opaque-handle/start?returnTo=%2Fdashboard");
            });
        });
    }

    @Test
    void unavailableOrganizationReturnsPublicMethodsWithoutInternalDetails() {
        OrganizationRepository organizations = mock(OrganizationRepository.class);
        LoginConnectionRepository connections = mock(LoginConnectionRepository.class);
        AuthMethodCatalog methods = mock(AuthMethodCatalog.class);
        when(methods.listMethods(null)).thenReturn(List.of(
                new AuthMethodResponse("local", "PASSWORD", "local", "Local", "/local")));
        EnterpriseIdentityRolloutProperties rollout = new EnterpriseIdentityRolloutProperties();
        rollout.setOidcLoginEnabled(true);
        rollout.setOrganizationAllowlist(java.util.Set.of("org-1"));
        when(organizations.findBySlug("unknown")).thenReturn(Optional.empty());

        EnterpriseLoginDiscoveryResponse response = new EnterpriseLoginDiscoveryService(
                organizations, connections, methods, rollout).discover("unknown", null);

        assertThat(response.organizations()).isEmpty();
        assertThat(response.publicMethods()).hasSize(1);
    }
}
