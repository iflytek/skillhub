package com.iflytek.skillhub.auth.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.springframework.http.HttpMethod;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RouteSecurityPolicyRegistryTest {

    private static final Set<String> ALL_SCOPES =
            Set.of("skill:read", "skill:publish", "skill:delete", "token:manage");

    private final RouteSecurityPolicyRegistry registry = new RouteSecurityPolicyRegistry();

    @Test
    void accessLevel_respectsMethodSpecificPublicRoutesAndProtectedFallback() {
        assertEquals(RouteSecurityPolicyRegistry.AccessLevel.PERMIT_ALL,
                registry.accessLevel("GET", "/api/v1/skills"));
        assertEquals(RouteSecurityPolicyRegistry.AccessLevel.AUTHENTICATED,
                registry.accessLevel("POST", "/api/v1/skills"));
        assertEquals(RouteSecurityPolicyRegistry.AccessLevel.AUTHENTICATED,
                registry.accessLevel("GET", "/api/v1/unlisted"));
    }

    @Test
    void accessLevel_matchesPublicSubpaths() {
        assertEquals(RouteSecurityPolicyRegistry.AccessLevel.PERMIT_ALL,
                registry.accessLevel("GET", "/api/v1/resolve/team/demo"));
    }

    @Test
    void reviewRoutesExposePublicListingButProtectCurrentUserMutations() {
        assertEquals(RouteSecurityPolicyRegistry.AccessLevel.PERMIT_ALL,
                registry.accessLevel("GET", "/api/v1/skills/10/reviews"));
        assertEquals(RouteSecurityPolicyRegistry.AccessLevel.AUTHENTICATED,
                registry.accessLevel("GET", "/api/v1/skills/10/reviews/me"));
        assertEquals(RouteSecurityPolicyRegistry.AccessLevel.AUTHENTICATED,
                registry.accessLevel("PUT", "/api/v1/skills/10/reviews/me"));
        assertEquals(RouteSecurityPolicyRegistry.AccessLevel.AUTHENTICATED,
                registry.accessLevel("DELETE", "/api/v1/skills/10/reviews/me"));
    }

    @Test
    void apiTokenPolicyAllowsCurrentUserReviewMutations() {
        assertTrue(registry.authorizeApiToken(
                "PUT", "/api/v1/skills/10/reviews/me", Set.of()).allowed());
        assertTrue(registry.authorizeApiToken(
                "DELETE", "/api/v1/skills/10/reviews/me", Set.of()).allowed());
    }

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
    void authorizationPolicies_shouldNotDeclareNamespaceBundleDownloadRoutes() {
        String v1Route = "/api/v1/namespaces/*/skills/" + "download";
        String webRoute = "/api/web/namespaces/*/skills/" + "download";
        boolean matchedV1 = registry.authorizationPolicies().stream()
                .anyMatch(policy -> policy.method() == HttpMethod.GET
                        && v1Route.equals(policy.pattern()));
        boolean matchedWeb = registry.authorizationPolicies().stream()
                .anyMatch(policy -> policy.method() == HttpMethod.GET
                        && webRoute.equals(policy.pattern()));

        assertFalse(matchedV1);
        assertFalse(matchedWeb);
        assertFalse(registry.authorizeApiToken("GET", "/api/v1/namespaces/global/skills/" + "download", Set.of()).allowed());
        assertFalse(registry.authorizeApiToken("GET", "/api/web/namespaces/global/skills/" + "download", Set.of()).allowed());
    }

    @Test
    void apiTokenPolicySupportsNativeCliRoutes() {
        assertTrue(registry.authorizeApiToken("GET", "/api/cli/v1/auth/whoami", Set.of()).allowed());
        assertTrue(registry.authorizeApiToken("GET", "/api/cli/v1/namespaces/team-a/skills", Set.of()).allowed());
        assertTrue(registry.authorizeApiToken("GET", "/api/cli/v1/skills/search", Set.of()).allowed());
        assertTrue(registry.authorizeApiToken("GET", "/api/cli/v1/skills/global/demo/resolve", Set.of()).allowed());
        assertFalse(registry.authorizeApiToken("POST", "/api/cli/v1/skills/global/publish", Set.of()).allowed());
        assertTrue(registry.authorizeApiToken("POST", "/api/cli/v1/skills/global/publish", Set.of("skill:publish")).allowed());
        assertFalse(registry.authorizeApiToken("POST", "/api/cli/v1/skills/global/publish/validate", Set.of()).allowed());
        assertTrue(registry.authorizeApiToken("POST", "/api/cli/v1/skills/global/publish/validate", Set.of("skill:publish")).allowed());
        assertTrue(registry.authorizeApiToken("DELETE", "/api/cli/v1/skills/global/demo", Set.of("skill:delete")).allowed());
    }

    @Test
    void routeAuthorizationRequiresAuthenticationForNativeCliNamespaceSync() {
        boolean matched = registry.authorizationPolicies().stream()
                .anyMatch(policy -> policy.method() == HttpMethod.GET
                        && "/api/cli/v1/namespaces/*/skills".equals(policy.pattern())
                        && policy.accessLevel() == RouteSecurityPolicyRegistry.AccessLevel.AUTHENTICATED);

        assertTrue(matched);
    }

    @Test
    void routeAuthorizationProtectsNativeCliRemoteDeleteByAuthenticationNotSuperAdminRole() {
        boolean matched = registry.authorizationPolicies().stream()
                .anyMatch(policy -> policy.method() == HttpMethod.DELETE
                        && "/api/cli/v1/skills/*/*".equals(policy.pattern())
                        && policy.accessLevel() == RouteSecurityPolicyRegistry.AccessLevel.AUTHENTICATED);

        assertTrue(matched);
    }

    @Test
    void shouldIgnoreCsrf_onlyForBearerTokensAndDeviceTokenFlow() {
        assertFalse(registry.shouldIgnoreCsrf("POST", "/api/v1/admin/users", null, false));
        assertFalse(registry.shouldIgnoreCsrf("POST", "/api/v1/auth/local/change-password", null, false));
        assertTrue(registry.shouldIgnoreCsrf("POST", "/not-api", "Bearer token", false));
        assertFalse(registry.shouldIgnoreCsrf("POST", "/not-api", "Bearer token", true));
        assertTrue(registry.shouldIgnoreCsrf("POST", "/api/v1/auth/device/code", null, false));
        assertTrue(registry.shouldIgnoreCsrf("POST", "/api/v1/auth/device/token", null, false));
        assertFalse(registry.shouldIgnoreCsrf("GET", "/api/v1/auth/device/code", null, false));
        assertFalse(registry.shouldIgnoreCsrf("POST", "/api/v1/auth/device/authorize", null, false));
        assertFalse(registry.shouldIgnoreCsrf("POST", "/ui/settings", null, false));
    }

    @Test
    void shouldProjectRequestContext_onlyForApiRoutes() {
        assertTrue(registry.shouldProjectRequestContext("/api/web/namespaces/team-a"));
        assertFalse(registry.shouldProjectRequestContext("/assets/index.css"));
    }

    @Test
    void requestPathUsesApplicationRouteBehindForwardedPrefix() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/skillhub/api/cli/v1/auth/whoami"
        );
        request.setContextPath("/skillhub");
        request.setServletPath("/api/cli/v1/auth/whoami");

        assertEquals("/api/cli/v1/auth/whoami", RouteSecurityPolicyRegistry.requestPath(request));
    }

    @Test
    void authorizeApiToken_allowsPublicLabelCatalogue() {
        assertTrue(registry.authorizeApiToken("GET", "/api/v1/labels", Set.of()).allowed());
        assertTrue(registry.authorizeApiToken("GET", "/api/web/labels", Set.of()).allowed());
    }

    @Test
    void authorizeApiToken_allowsStarAndRatingWritesWithoutSkillDeleteScope() {
        assertTrue(registry.authorizeApiToken("PUT", "/api/v1/skills/42/star", Set.of("skill:read")).allowed());
        assertTrue(registry.authorizeApiToken("DELETE", "/api/v1/skills/42/star", Set.of("skill:read")).allowed());
        assertTrue(registry.authorizeApiToken("PUT", "/api/v1/skills/42/rating", Set.of("skill:read")).allowed());
    }

    @Test
    void authorizationPolicies_declareStarAndRatingWritesBeforeTheSuperAdminDeleteRule() {
        int unstar = indexOf(HttpMethod.DELETE, "/api/v1/skills/*/star");
        int hardDelete = indexOf(HttpMethod.DELETE, "/api/v1/skills/*/*");

        assertTrue(unstar >= 0, "un-star must have its own authorization policy");
        assertTrue(hardDelete >= 0);
        assertTrue(unstar < hardDelete, "un-star must be matched before the SUPER_ADMIN hard-delete rule");
    }

    @Test
    void authorizeApiToken_allowsDownloadsBelowTheDownloadRoot() {
        assertTrue(registry.authorizeApiToken("GET", "/api/v1/download/global/demo-skill", Set.of()).allowed());
    }

    @Test
    void apiTokenPolicies_coverEveryTokenReachableAuthorizationRoute() {
        List<String> gaps = new ArrayList<>();

        for (RouteSecurityPolicyRegistry.RouteAuthorizationPolicy policy : registry.authorizationPolicies()) {
            if (policy.accessLevel() == RouteSecurityPolicyRegistry.AccessLevel.ROLE_PROTECTED) {
                continue;
            }
            if (!policy.pattern().startsWith("/api/")) {
                continue;
            }
            String key = RouteSecurityPolicyRegistry.routeKey(policy.method(), policy.pattern());
            if (registry.sessionOnlyRoutes().contains(key)) {
                continue;
            }

            String method = policy.method() == null ? "GET" : policy.method().name();
            String path = samplePath(policy.pattern());
            if (!registry.authorizeApiToken(method, path, ALL_SCOPES).allowed()) {
                gaps.add(key);
            }
        }

        assertTrue(gaps.isEmpty(),
                "Authorization routes with no API-token policy and no session-only declaration: " + gaps);
    }

    @Test
    void sessionOnlyRoutes_areRejectedForApiTokens() {
        for (String route : registry.sessionOnlyRoutes()) {
            String[] parts = route.split(" ", 2);
            String method = "ANY".equals(parts[0]) ? "POST" : parts[0];
            assertFalse(registry.authorizeApiToken(method, samplePath(parts[1]), ALL_SCOPES).allowed(),
                    "session-only route must stay closed to API tokens: " + route);
        }
    }

    private int indexOf(HttpMethod method, String pattern) {
        List<RouteSecurityPolicyRegistry.RouteAuthorizationPolicy> policies = registry.authorizationPolicies();
        for (int i = 0; i < policies.size(); i++) {
            if (policies.get(i).method() == method && pattern.equals(policies.get(i).pattern())) {
                return i;
            }
        }
        return -1;
    }

    private static String samplePath(String pattern) {
        return pattern.replace("/**", "/sample/leaf").replace("*", "sample");
    }
}
