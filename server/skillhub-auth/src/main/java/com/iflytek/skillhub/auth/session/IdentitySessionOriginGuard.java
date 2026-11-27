package com.iflytek.skillhub.auth.session;

import com.iflytek.skillhub.auth.connection.persistence.LoginConnectionRepository;
import com.iflytek.skillhub.auth.federation.association.ExternalIdentityRepository;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.organization.OrganizationMembershipRepository;
import com.iflytek.skillhub.domain.organization.OrganizationMembershipStatus;
import com.iflytek.skillhub.domain.organization.OrganizationRepository;
import com.iflytek.skillhub.domain.organization.OrganizationStatus;
import jakarta.servlet.http.HttpSession;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Rechecks enterprise session authority before projecting a request to application code. */
@Service
public class IdentitySessionOriginGuard {
    private final IdentitySessionOriginRepository origins;
    private final OrganizationRepository organizations;
    private final OrganizationMembershipRepository memberships;
    private final LoginConnectionRepository connections;
    private final ExternalIdentityRepository externalIdentities;

    public IdentitySessionOriginGuard(IdentitySessionOriginRepository origins,
                                      OrganizationRepository organizations,
                                      OrganizationMembershipRepository memberships,
                                      LoginConnectionRepository connections,
                                      ExternalIdentityRepository externalIdentities) {
        this.origins = origins;
        this.organizations = organizations;
        this.memberships = memberships;
        this.connections = connections;
        this.externalIdentities = externalIdentities;
    }

    public boolean isAllowed(HttpSession session, PlatformPrincipal principal) {
        if (session == null || principal == null || !isEnterprise(principal)) {
            return true;
        }
        String organizationId = text(session.getAttribute("enterprise.organizationId"));
        String membershipId = text(session.getAttribute("enterprise.membershipId"));
        String connectionId = text(session.getAttribute("enterprise.connectionId"));
        if (organizationId == null || membershipId == null || connectionId == null) {
            return false;
        }
        var origin = origins.findBySessionKeyHash(IdentitySessionOriginService.hash(session.getId())).orElse(null);
        if (origin == null || !originMatches(origin, principal.userId(), organizationId, membershipId, connectionId)) {
            return false;
        }
        var organization = organizations.findById(organizationId).orElse(null);
        if (organization == null || organization.getStatus() != OrganizationStatus.ACTIVE
                || organization.getAuthorityVersion() != origin.getOrganizationAuthorityVersion()) {
            return false;
        }
        var membership = memberships.findByOrganizationIdAndId(organizationId, membershipId).orElse(null);
        if (membership == null || !principal.userId().equals(membership.getUserId())
                || membership.getStatus() != OrganizationMembershipStatus.ACTIVE
                || membership.getAuthorityVersion() != origin.getMembershipAuthorityVersion()) {
            return false;
        }
        var connection = connections.findByOrganizationIdAndId(organizationId, connectionId).orElse(null);
        if (connection == null || connection.getStatus()
                != com.iflytek.skillhub.auth.connection.persistence.LoginConnectionStatus.ACTIVE) {
            return false;
        }
        return externalIdentities.findByOrganizationIdAndId(organizationId, origin.getExternalIdentityId())
                .map(identity -> identity.isActive()).orElse(false);
    }

    private static boolean originMatches(IdentitySessionOrigin origin, String userId,
                                         String organizationId, String membershipId, String connectionId) {
        return Objects.equals(origin.getUserId(), userId)
                && Objects.equals(origin.getOrganizationId(), organizationId)
                && Objects.equals(origin.getMembershipId(), membershipId)
                && Objects.equals(origin.getLoginConnectionId(), connectionId);
    }

    private static boolean isEnterprise(PlatformPrincipal principal) {
        return principal.oauthProvider() != null && principal.oauthProvider().startsWith("enterprise-oidc:");
    }

    private static String text(Object value) {
        return value instanceof String string && !string.isBlank() ? string : null;
    }
}
