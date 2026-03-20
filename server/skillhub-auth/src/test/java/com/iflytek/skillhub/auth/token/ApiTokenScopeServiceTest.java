package com.iflytek.skillhub.auth.token;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiTokenScopeServiceTest {

    private final ApiTokenScopeService scopeService =
            new ApiTokenScopeService(new ObjectMapper(), new RouteSecurityPolicyRegistry());

    @Test
    void parseScopesShouldNormalizeJsonArray() {
        Set<String> scopes = scopeService.parseScopes("[\"skill:read\", \"skill:publish\", \"skill:read\", \"  \"]");

        assertEquals(Set.of("skill:read", "skill:publish"), scopes);
    }

    @Test
    void authorizeShouldAllowCliWhoamiWithoutScope() {
        ApiTokenScopeService.AuthorizationDecision decision = scopeService.authorize(
            "GET",
            "/api/v1/whoami",
            Set.of()
        );

        assertTrue(decision.allowed());
    }

    @Test
    void authorizeShouldRequirePublishScopeForPortalPublish() {
        ApiTokenScopeService.AuthorizationDecision denied = scopeService.authorize(
            "POST",
            "/api/v1/skills/team-a/publish",
            Set.of("skill:read")
        );

        assertFalse(denied.allowed());
        assertEquals("skill:publish", denied.requiredScope());

        ApiTokenScopeService.AuthorizationDecision allowed = scopeService.authorize(
            "POST",
            "/api/v1/skills/team-a/publish",
            Set.of("skill:publish")
        );

        assertTrue(allowed.allowed());
    }

    @Test
    void authorizeShouldRequireDeleteScopeForHardDeleteEndpoint() {
        ApiTokenScopeService.AuthorizationDecision denied = scopeService.authorize(
                "DELETE",
                "/api/v1/skills/team-a/demo-skill",
                Set.of("skill:publish")
        );

        assertFalse(denied.allowed());
        assertEquals("skill:delete", denied.requiredScope());

        ApiTokenScopeService.AuthorizationDecision allowed = scopeService.authorize(
                "DELETE",
                "/api/v1/skills/team-a/demo-skill",
                Set.of("skill:delete")
        );

        assertTrue(allowed.allowed());
    }

    @Test
    void authorizeShouldRequireTokenManageScopeForTokenEndpoints() {
        ApiTokenScopeService.AuthorizationDecision decision = scopeService.authorize(
            "GET",
            "/api/v1/tokens",
            Set.of("skill:publish")
        );

        assertFalse(decision.allowed());
        assertEquals("token:manage", decision.requiredScope());
    }

    @Test
    void authorizeShouldDenyUnsupportedAuthenticatedEndpoints() {
        ApiTokenScopeService.AuthorizationDecision decision = scopeService.authorize(
            "GET",
            "/api/v1/me/skills",
            Set.of("skill:read", "skill:publish")
        );

        assertFalse(decision.allowed());
        assertEquals("API token cannot access endpoint: /api/v1/me/skills", decision.message());
    }

    @Test
    void authorizeShouldAllowPublicNamespaceReadWithoutScope() {
        ApiTokenScopeService.AuthorizationDecision decision = scopeService.authorize(
                "GET",
                "/api/v1/namespaces/team-a",
                Set.of()
        );

        assertTrue(decision.allowed());
    }

    @Test
    void authorizeShouldPermitAuthMeWithoutScope() {
        ApiTokenScopeService.AuthorizationDecision decision = scopeService.authorize(
                "GET",
                "/api/v1/auth/me",
                Set.of()
        );

        assertTrue(decision.allowed());
    }
}
