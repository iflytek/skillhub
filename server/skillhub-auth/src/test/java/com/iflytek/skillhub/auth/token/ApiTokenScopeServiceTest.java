package com.iflytek.skillhub.auth.token;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiTokenScopeServiceTest {

    private final ApiTokenScopeService scopeService =
            new ApiTokenScopeService(new ObjectMapper(), new RouteSecurityPolicyRegistry());

    @Test
    void namespaceCapabilitiesRequireAnExplicitGrantFromTheNewIssuer() {
        var oldToken = org.mockito.Mockito.mock(com.iflytek.skillhub.auth.entity.ApiToken.class);
        String stored = "[\"skill:read\",\" namespace:manage \"]";
        org.mockito.Mockito.when(oldToken.getScopeJson()).thenReturn(stored);
        assertEquals(Set.of("skill:read"), scopeService.issuedScopes(oldToken));
        var issued = new com.iflytek.skillhub.auth.entity.ApiToken("user", "DSH", "prefix", "hash", stored);
        assertEquals(Set.of("skill:read", "namespace:read", "namespace:manage"),
                scopeService.expandScopes(scopeService.issuedScopes(issued)));
    }

    @Test
    void legacyAliasExpandsAndDeduplicatesWithoutChangingRawScopes() {
        String json = "[\"skill:read\",\"skill:publish\",\"skill:submit\",\"skill:publish\"]";
        assertEquals(Set.of("skill:read", "skill:publish", "skill:submit"), scopeService.parseScopes(json));
        assertEquals(Set.of("skill:read", "skill:submit", "skill:publish-private", "skill:manage",
                "suite:legacy-write"), scopeService.effectiveCapabilities(json));
    }

    @Test
    void emptyAndMalformedStoredScopesFailClosed() {
        for (String json : List.of("", "[]", "null", "not-json", "{}")) {
            assertEquals(Set.of(), scopeService.effectiveCapabilities(json));
        }
        assertEquals(Set.of(), scopeService.effectiveCapabilities(null));
    }

    @Test
    void issuanceAcceptsKnownScopesAndEmptyListButRejectsUnknownAndInternalScopes() {
        scopeService.validateScopes(List.of());
        scopeService.validateScopes(List.of("skill:read", "skill:submit", "skill:publish-private",
                "skill:manage", "skill:delete", "token:manage", "skill:publish"));
        for (String unknown : List.of("no-such:scope", "suite:legacy-write", " skill:read", "")) {
            var exception = assertThrows(DomainBadRequestException.class,
                    () -> scopeService.validateScopes(List.of(unknown)));
            assertEquals("error.apiToken.scope.unknown", exception.messageCode());
            assertEquals(unknown, exception.messageArgs()[0]);
        }
    }

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
        assertEquals("skill:submit", denied.requiredScope());

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
    void managementScopesApplyOnlyToTheNamedRoutes() {
        assertTrue(scopeService.authorize("GET", "/api/v1/me/skills", Set.of("skill:read")).allowed());
        assertFalse(scopeService.authorize("GET", "/api/v1/me/skills", Set.of()).allowed());
        String path = "/api/v1/namespaces/team-a/members";
        assertTrue(scopeService.authorize("GET", path, Set.of("namespace:manage")).allowed());
        assertTrue(scopeService.authorize("POST", path, Set.of("namespace:manage")).allowed());
        assertFalse(scopeService.authorize("POST", path, Set.of("namespace:read")).allowed());
        assertFalse(scopeService.authorize("GET", path, Set.of("skill:publish")).allowed());
        assertFalse(scopeService.authorize("POST", "/api/v1/namespaces/team-a/member-candidates/search",
                Set.of("namespace:manage")).allowed());
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
