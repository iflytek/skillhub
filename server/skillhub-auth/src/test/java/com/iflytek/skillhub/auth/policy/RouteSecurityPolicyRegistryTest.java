package com.iflytek.skillhub.auth.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;

class RouteSecurityPolicyRegistryTest {

    private final RouteSecurityPolicyRegistry registry = new RouteSecurityPolicyRegistry();

    @Test
    void authorizeApiToken_requiresPublishScopeForPublishEndpoints() {
        var denied = registry.authorizeApiToken("POST", "/api/web/skills/global/publish", Set.of("skill:read"));
        var allowed = registry.authorizeApiToken("POST", "/api/web/skills/global/publish", Set.of("skill:publish"));

        assertFalse(denied.allowed());
        assertEquals("skill:publish", denied.requiredScope());
        assertTrue(allowed.allowed());
    }

    @Test
    void authorizeApiToken_requiresDeleteScopeForHardDeleteEndpoint() {
        var denied = registry.authorizeApiToken("DELETE", "/api/v1/skills/global/demo-skill", Set.of("skill:publish"));
        var allowed = registry.authorizeApiToken("DELETE", "/api/v1/skills/global/demo-skill", Set.of("skill:delete"));

        assertFalse(denied.allowed());
        assertEquals("skill:delete", denied.requiredScope());
        assertTrue(allowed.allowed());
    }

    @Test
    void authorizationPolicies_shouldDeclareSuperAdminDeleteRuleForHardDeleteEndpoint() {
        boolean matched = registry.authorizationPolicies().stream()
                .anyMatch(policy -> policy.method() == HttpMethod.DELETE
                        && "/api/v1/skills/*/*".equals(policy.pattern())
                        && policy.accessLevel() == RouteSecurityPolicyRegistry.AccessLevel.ROLE_PROTECTED
                        && Set.of(policy.roles()).contains("SUPER_ADMIN"));

        assertTrue(matched);
    }

    @Test
    void authorizationPolicies_shouldKeepPublicLabelsEndpointsAnonymous() {
        boolean matchedV1 = registry.authorizationPolicies().stream()
                .anyMatch(policy -> policy.method() == HttpMethod.GET
                        && "/api/v1/labels".equals(policy.pattern())
                        && policy.accessLevel() == RouteSecurityPolicyRegistry.AccessLevel.PERMIT_ALL);
        boolean matchedWeb = registry.authorizationPolicies().stream()
                .anyMatch(policy -> policy.method() == HttpMethod.GET
                        && "/api/web/labels".equals(policy.pattern())
                        && policy.accessLevel() == RouteSecurityPolicyRegistry.AccessLevel.PERMIT_ALL);

        assertTrue(matchedV1);
        assertTrue(matchedWeb);
    }

    @Test
    void authorizationPolicies_shouldRequireAuthenticationForNamespaceDiscovery() {
        boolean matchedV1 = registry.authorizationPolicies().stream()
                .anyMatch(policy -> policy.method() == HttpMethod.GET
                        && "/api/v1/namespaces".equals(policy.pattern())
                        && policy.accessLevel() == RouteSecurityPolicyRegistry.AccessLevel.AUTHENTICATED);
        boolean matchedWeb = registry.authorizationPolicies().stream()
                .anyMatch(policy -> policy.method() == HttpMethod.GET
                        && "/api/web/namespaces".equals(policy.pattern())
                        && policy.accessLevel() == RouteSecurityPolicyRegistry.AccessLevel.AUTHENTICATED);

        assertTrue(matchedV1);
        assertTrue(matchedWeb);
    }

    @Test
    void authorizationPolicies_shouldPermitAnonymousUassCallback() {
        boolean matched = registry.authorizationPolicies().stream()
                .anyMatch(policy -> policy.method() == HttpMethod.GET
                        && "/api/v1/auth/uass/callback".equals(policy.pattern())
                        && policy.accessLevel() == RouteSecurityPolicyRegistry.AccessLevel.PERMIT_ALL);

        assertTrue(matched);
    }

    @Test
    void authorizationPolicies_shouldPermitAnonymousUassLoginInitiationEndpoints() {
        boolean loginUrlMatched = registry.authorizationPolicies().stream()
                .anyMatch(policy -> policy.method() == HttpMethod.GET
                        && "/api/v1/auth/uass/login-url".equals(policy.pattern())
                        && policy.accessLevel() == RouteSecurityPolicyRegistry.AccessLevel.PERMIT_ALL);
        boolean redirectMatched = registry.authorizationPolicies().stream()
                .anyMatch(policy -> policy.method() == HttpMethod.GET
                        && "/api/v1/auth/uass/redirect".equals(policy.pattern())
                        && policy.accessLevel() == RouteSecurityPolicyRegistry.AccessLevel.PERMIT_ALL);

        assertTrue(loginUrlMatched);
        assertTrue(redirectMatched);
    }

    @Test
    void authorizationPolicies_shouldExposeUassStatusAndProtectLogout() {
        boolean statusMatched = registry.authorizationPolicies().stream()
                .anyMatch(policy -> policy.method() == HttpMethod.GET
                        && "/api/v1/auth/uass/status".equals(policy.pattern())
                        && policy.accessLevel() == RouteSecurityPolicyRegistry.AccessLevel.PERMIT_ALL);
        boolean logoutMatched = registry.authorizationPolicies().stream()
                .anyMatch(policy -> policy.method() == HttpMethod.POST
                        && "/api/v1/auth/uass/logout".equals(policy.pattern())
                        && policy.accessLevel() == RouteSecurityPolicyRegistry.AccessLevel.AUTHENTICATED);

        assertTrue(statusMatched);
        assertTrue(logoutMatched);
    }

    @Test
    void shouldIgnoreCsrf_forBearerAndApiPaths() {
        assertTrue(registry.shouldIgnoreCsrf("/api/v1/admin/users", null));
        assertTrue(registry.shouldIgnoreCsrf("/not-api", "Bearer token"));
        assertFalse(registry.shouldIgnoreCsrf(null, null));
        assertFalse(registry.shouldIgnoreCsrf("/ui/settings", null));
    }

    @Test
    void shouldProjectRequestContext_onlyForApiRoutes() {
        assertTrue(registry.shouldProjectRequestContext("/api/web/namespaces/team-a"));
        assertFalse(registry.shouldProjectRequestContext(null));
        assertFalse(registry.shouldProjectRequestContext("/assets/index.css"));
    }

    @Test
    void authorizeApiToken_allowsNonApiPathsAndRejectsUnsupportedApiEndpoints() {
        var nonApiDecision = registry.authorizeApiToken("GET", "/login", Set.of());
        var unsupportedDecision = registry.authorizeApiToken("PATCH", "/api/v1/unknown", Set.of("token:manage"));

        assertTrue(nonApiDecision.allowed());
        assertFalse(unsupportedDecision.allowed());
        assertEquals("API token cannot access endpoint: /api/v1/unknown", unsupportedDecision.message());
    }

    @Test
    void authorizeApiToken_treatsNullMethodAsUnsupportedForMethodScopedPolicy() {
        var decision = registry.authorizeApiToken(null, "/api/v1/whoami", Set.of("token:manage"));

        assertFalse(decision.allowed());
        assertEquals("API token cannot access endpoint: /api/v1/whoami", decision.message());
    }

    @Test
    void routeAuthorizationPolicy_buildsMatchersForMethodSpecificAndMethodAgnosticRules() {
        var methodAgnostic = RouteSecurityPolicyRegistry.RouteAuthorizationPolicy.permitAll(null, "/api/v1/auth/me")
                .toRequestMatcher();
        var methodSpecific = RouteSecurityPolicyRegistry.RouteAuthorizationPolicy.roles(
                HttpMethod.POST,
                "/api/v1/auth/uass/logout",
                "SUPER_ADMIN"
        ).toRequestMatcher();
        MockHttpServletRequest postRequest = new MockHttpServletRequest("POST", "/api/v1/auth/uass/logout");
        MockHttpServletRequest getRequest = new MockHttpServletRequest("GET", "/api/v1/auth/uass/logout");
        MockHttpServletRequest authMeRequest = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        postRequest.setServletPath("/api/v1/auth/uass/logout");
        getRequest.setServletPath("/api/v1/auth/uass/logout");
        authMeRequest.setServletPath("/api/v1/auth/me");

        assertTrue(methodAgnostic.matches(authMeRequest));
        assertTrue(methodSpecific.matches(postRequest));
        assertFalse(methodSpecific.matches(getRequest));
    }
}
