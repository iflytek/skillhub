package com.iflytek.skillhub.auth.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.connection.persistence.LoginConnection;
import com.iflytek.skillhub.auth.connection.persistence.LoginConnectionRepository;
import com.iflytek.skillhub.auth.connection.persistence.LoginConnectionStatus;
import com.iflytek.skillhub.auth.federation.association.ExternalIdentity;
import com.iflytek.skillhub.auth.federation.association.ExternalIdentityRepository;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.organization.Organization;
import com.iflytek.skillhub.domain.organization.OrganizationMembership;
import com.iflytek.skillhub.domain.organization.OrganizationMembershipRepository;
import com.iflytek.skillhub.domain.organization.OrganizationMembershipStatus;
import com.iflytek.skillhub.domain.organization.OrganizationRepository;
import com.iflytek.skillhub.domain.organization.OrganizationStatus;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IdentitySessionOriginGuardTest {
    @Test
    void rejectsSessionAfterOrganizationAuthorityChanges() {
        HttpSession session = mock(HttpSession.class);
        when(session.getId()).thenReturn("session-1");
        when(session.getAttribute("enterprise.organizationId")).thenReturn("org-1");
        when(session.getAttribute("enterprise.membershipId")).thenReturn("membership-1");
        when(session.getAttribute("enterprise.connectionId")).thenReturn("connection-1");
        PlatformPrincipal principal = new PlatformPrincipal("user-1", "User", null, null,
                "enterprise-oidc:connection-1", java.util.Set.of("USER"));

        IdentitySessionOriginRepository origins = mock(IdentitySessionOriginRepository.class);
        OrganizationRepository organizations = mock(OrganizationRepository.class);
        OrganizationMembershipRepository memberships = mock(OrganizationMembershipRepository.class);
        LoginConnectionRepository connections = mock(LoginConnectionRepository.class);
        ExternalIdentityRepository externalIdentities = mock(ExternalIdentityRepository.class);
        IdentitySessionOrigin origin = IdentitySessionOrigin.create("origin-1",
                IdentitySessionOriginService.hash("session-1"), "user-1", "org-1", "membership-1",
                "connection-1", "external-1", Instant.parse("2026-11-26T00:00:00Z"),
                "oidc.id_token", 1, 1);
        when(origins.findBySessionKeyHash(IdentitySessionOriginService.hash("session-1")))
                .thenReturn(Optional.of(origin));
        Organization organization = mock(Organization.class);
        when(organization.getStatus()).thenReturn(OrganizationStatus.ACTIVE);
        when(organization.getAuthorityVersion()).thenReturn(2L);
        when(organizations.findById("org-1")).thenReturn(Optional.of(organization));

        IdentitySessionOriginGuard guard = new IdentitySessionOriginGuard(origins, organizations, memberships,
                connections, externalIdentities);

        assertThat(guard.isAllowed(session, principal)).isFalse();
    }

    @Test
    void leavesPublicSessionTypesUnchanged() {
        IdentitySessionOriginGuard guard = new IdentitySessionOriginGuard(
                mock(IdentitySessionOriginRepository.class), mock(OrganizationRepository.class),
                mock(OrganizationMembershipRepository.class), mock(LoginConnectionRepository.class),
                mock(ExternalIdentityRepository.class));
        PlatformPrincipal principal = new PlatformPrincipal("user-1", "User", null, null,
                "github", java.util.Set.of("USER"));

        assertThat(guard.isAllowed(null, principal)).isTrue();
    }
}
