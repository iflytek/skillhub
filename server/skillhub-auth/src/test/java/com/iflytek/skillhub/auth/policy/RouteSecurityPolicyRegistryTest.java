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

    private static final Set<String> PUBLISH_CAPABILITIES =
            Set.of("skill:submit", "skill:publish-private", "skill:manage", "suite:legacy-write");
    private static final Set<String> ALL_SCOPES = Set.of(
            "skill:read", "skill:submit", "skill:publish-private", "skill:manage",
            "suite:legacy-write", "skill:delete", "token:manage");

    private final RouteSecurityPolicyRegistry registry = new RouteSecurityPolicyRegistry();

    @Test
    void sitePresentationIsPublicReadOnlyAndManagementIsNeverAnApiTokenCapability() {
        for (String route : List.of("/api/v1/site-presentation", "/api/v1/site-presentation/assets/asset-id")) {
            assertEquals(RouteSecurityPolicyRegistry.AccessLevel.PERMIT_ALL, registry.accessLevel("GET", route));
            assertEquals(RouteSecurityPolicyRegistry.AccessLevel.AUTHENTICATED, registry.accessLevel("PUT", route));
            assertTrue(registry.authorizeApiToken("GET", route, Set.of()).allowed());
            assertFalse(registry.authorizeApiToken("PUT", route, ALL_SCOPES).allowed());
        }
        assertFalse(registry.authorizeApiToken("PUT", "/api/v1/admin/system-config/presentation", ALL_SCOPES).allowed());
        assertFalse(registry.authorizeApiToken("POST", "/api/v1/admin/system-config/presentation/assets", ALL_SCOPES).allowed());
    }

    @Test
    void visibilityWritesRequirePublishScopeAndAuthenticatedReads() {
        for (String prefix : List.of("/api/v1", "/api/web")) {
            String route = prefix + "/skills/team/foo/visibility";
            assertEquals(RouteSecurityPolicyRegistry.AccessLevel.AUTHENTICATED, registry.accessLevel("GET", route));
            assertEquals(RouteSecurityPolicyRegistry.AccessLevel.AUTHENTICATED, registry.accessLevel("POST", route));
            assertFalse(registry.authorizeApiToken("POST", route, Set.of("skill:read")).allowed());
            assertTrue(registry.authorizeApiToken("POST", route, Set.of("skill:manage")).allowed());
        }
    }

    @Test
    void packagePreviewIsAuthenticatedAndRequiresPublishScope() {
        for (String prefix : java.util.List.of("/api/v1", "/api/web")) {
            assertEquals(RouteSecurityPolicyRegistry.AccessLevel.AUTHENTICATED,
                    registry.accessLevel("POST", prefix + "/skills/package-preview"));
            assertFalse(registry.authorizeApiToken("POST", prefix + "/skills/package-preview", Set.of("skill:read")).allowed());
            assertTrue(registry.authorizeApiToken(
                    "POST", prefix + "/skills/package-preview", Set.of("skill:submit")).allowed());
        }
    }

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
    void currentAvatarMayBePublicButCandidateAndReviewRoutesRequireAuthentication() {
        assertEquals(RouteSecurityPolicyRegistry.AccessLevel.PERMIT_ALL,
                registry.accessLevel("GET", "/api/v1/skills/global/demo/avatar"));
        assertEquals(RouteSecurityPolicyRegistry.AccessLevel.PERMIT_ALL,
                registry.accessLevel("GET", "/api/web/skills/global/demo/avatar"));
        assertEquals(RouteSecurityPolicyRegistry.AccessLevel.AUTHENTICATED,
                registry.accessLevel("GET", "/api/v1/skills/global/demo/avatar/candidates/1/image"));
        assertEquals(RouteSecurityPolicyRegistry.AccessLevel.AUTHENTICATED,
                registry.accessLevel("GET", "/api/v1/reviews"));
        assertEquals(RouteSecurityPolicyRegistry.AccessLevel.AUTHENTICATED,
                registry.accessLevel("GET", "/api/web/reviews"));
    }

    @Test
    void stagedAvatarUploadRequiresSessionWhilePreviewRequiresAnAuthenticatedUploader() {
        for (String prefix : List.of("/api/v1", "/api/web")) {
            String upload = prefix + "/skills/avatar-uploads";
            assertEquals(RouteSecurityPolicyRegistry.AccessLevel.AUTHENTICATED, registry.accessLevel("POST", upload));
            assertTrue(registry.sessionOnlyRoutes().contains("POST " + upload));
            assertFalse(registry.authorizeApiToken("POST", upload, ALL_SCOPES).allowed());
            assertEquals(RouteSecurityPolicyRegistry.AccessLevel.AUTHENTICATED,
                    registry.accessLevel("GET", upload + "/upload-id/image"));
        }
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
        var allowed = registry.authorizeApiToken("POST", "/api/web/skills/global/publish", Set.of("skill:submit"));

        assertFalse(denied.allowed());
        assertEquals("skill:submit", denied.requiredScope());
        assertTrue(allowed.allowed());
    }

    @Test
    void suiteRoutes_keepDiscoveryPublicAndProtectMutations() {
        assertEquals(RouteSecurityPolicyRegistry.AccessLevel.PERMIT_ALL,
                registry.accessLevel("GET", "/api/v1/resources"));
        assertEquals(RouteSecurityPolicyRegistry.AccessLevel.PERMIT_ALL,
                registry.accessLevel("GET", "/api/web/resources"));
        assertEquals(RouteSecurityPolicyRegistry.AccessLevel.AUTHENTICATED,
                registry.accessLevel("GET", "/api/web/me/suites"));
        assertEquals(RouteSecurityPolicyRegistry.AccessLevel.PERMIT_ALL,
                registry.accessLevel("GET", "/api/v1/suites/global/starter"));
        assertEquals(RouteSecurityPolicyRegistry.AccessLevel.PERMIT_ALL,
                registry.accessLevel("GET", "/api/v1/suites/global/starter/labels"));
        assertEquals(RouteSecurityPolicyRegistry.AccessLevel.AUTHENTICATED,
                registry.accessLevel("PUT", "/api/v1/suites/global/starter/labels/official"));
        assertEquals(RouteSecurityPolicyRegistry.AccessLevel.AUTHENTICATED,
                registry.accessLevel("DELETE", "/api/web/suites/global/starter/labels/official"));
        assertEquals(RouteSecurityPolicyRegistry.AccessLevel.PERMIT_ALL,
                registry.accessLevel("POST", "/api/v1/suites/global/starter/install-plan"));
        assertEquals(RouteSecurityPolicyRegistry.AccessLevel.AUTHENTICATED,
                registry.accessLevel("GET", "/api/v1/suites/member-candidates"));
        assertEquals(RouteSecurityPolicyRegistry.AccessLevel.AUTHENTICATED,
                registry.accessLevel("POST", "/api/v1/suites"));
    }

    @Test
    void skillLabelApiTokenPolicies_requirePublishScope() {
        for (String path : new String[] {
                "/api/v1/skills/global/demo/labels/official", "/api/web/skills/global/demo/labels/official"}) {
            var deniedAttach = registry.authorizeApiToken("PUT", path, Set.of("skill:read"));
            assertFalse(deniedAttach.allowed());
            assertEquals("skill:manage", deniedAttach.requiredScope());
            var deniedDetach = registry.authorizeApiToken("DELETE", path, Set.of("skill:read"));
            assertFalse(deniedDetach.allowed());
            assertEquals("skill:manage", deniedDetach.requiredScope());
            assertFalse(registry.authorizeApiToken("PUT", path, Set.of()).allowed());
            assertFalse(registry.authorizeApiToken("DELETE", path, Set.of()).allowed());
            assertTrue(registry.authorizeApiToken("PUT", path, Set.of("skill:manage")).allowed());
            assertTrue(registry.authorizeApiToken("DELETE", path, Set.of("skill:manage")).allowed());
        }
        assertTrue(registry.authorizeApiToken("GET", "/api/v1/skills/global/demo/labels", Set.of()).allowed());
    }

    @Test
    void suiteApiTokenPolicies_requirePublishScopeAndKeepReviewSessionOnly() {
        assertTrue(registry.authorizeApiToken(
                "GET", "/api/v1/suites/global/starter", Set.of()).allowed());
        assertTrue(registry.authorizeApiToken(
                "POST", "/api/v1/suites/global/starter/install-plan", Set.of()).allowed());
        var deniedAttachLabel = registry.authorizeApiToken(
                "PUT", "/api/v1/suites/global/starter/labels/official", Set.of("skill:read"));
        var allowedAttachLabel = registry.authorizeApiToken(
                "PUT", "/api/v1/suites/global/starter/labels/official", Set.of("suite:legacy-write"));
        assertFalse(deniedAttachLabel.allowed());
        assertEquals("suite:legacy-write", deniedAttachLabel.requiredScope());
        assertTrue(allowedAttachLabel.allowed());

        var deniedCreate = registry.authorizeApiToken("POST", "/api/v1/suites", Set.of("skill:read"));
        var allowedCreate = registry.authorizeApiToken("POST", "/api/v1/suites", Set.of("suite:legacy-write"));
        var deniedSubmit = registry.authorizeApiToken(
                "POST", "/api/v1/suites/8/versions/42/submit", Set.of("skill:read"));

        assertFalse(deniedCreate.allowed());
        assertEquals("suite:legacy-write", deniedCreate.requiredScope());
        assertTrue(allowedCreate.allowed());
        assertFalse(deniedSubmit.allowed());
        assertEquals("suite:legacy-write", deniedSubmit.requiredScope());
        assertFalse(registry.authorizeApiToken(
                "POST", "/api/v1/suites/reviews/7/approve", ALL_SCOPES).allowed());
    }

    @Test
    void authorizeApiToken_requiresPublishScopeForSecurityScanRetry() {
        var denied = registry.authorizeApiToken(
                "POST", "/api/v1/skills/8/versions/42/security-audit/retry", Set.of("skill:read"));
        var allowed = registry.authorizeApiToken(
                "POST", "/api/v1/skills/8/versions/42/security-audit/retry", Set.of("skill:manage"));

        assertFalse(denied.allowed());
        assertEquals("skill:manage", denied.requiredScope());
        assertTrue(allowed.allowed());
    }

    @Test
    void authorizeApiToken_requiresDeleteScopeForHardDeleteEndpoint() {
        var denied = registry.authorizeApiToken("DELETE", "/api/v1/skills/global/demo-skill", PUBLISH_CAPABILITIES);
        var allowed = registry.authorizeApiToken("DELETE", "/api/v1/skills/global/demo-skill", Set.of("skill:delete"));

        assertFalse(denied.allowed());
        assertEquals("skill:delete", denied.requiredScope());
        assertTrue(allowed.allowed());
    }

    @Test
    void authorizeApiToken_requiresPublishScopeForArchiveAndUnarchive() {
        for (String prefix : List.of("/api/v1", "/api/web")) {
            for (String action : List.of("archive", "unarchive")) {
                String path = prefix + "/skills/global/demo-skill/" + action;
                var denied = registry.authorizeApiToken("POST", path, Set.of("skill:read", "skill:delete"));
                var allowed = registry.authorizeApiToken("POST", path, Set.of("skill:manage"));

                assertFalse(denied.allowed(), path);
                assertEquals("skill:manage", denied.requiredScope(), path);
                assertTrue(allowed.allowed(), path);
            }
        }
    }

    @Test
    void authorizeApiToken_requiresDeleteScopeForVersionDeleteEndpoint() {
        for (String prefix : List.of("/api/v1", "/api/web")) {
            String path = prefix + "/skills/global/demo-skill/versions/1.2.3";
            var denied = registry.authorizeApiToken("DELETE", path, PUBLISH_CAPABILITIES);
            var allowed = registry.authorizeApiToken("DELETE", path, Set.of("skill:delete"));

            assertFalse(denied.allowed(), path);
            assertEquals("skill:delete", denied.requiredScope(), path);
            assertTrue(allowed.allowed(), path);
        }
    }

    @Test
    void authorizeApiToken_requiresPublishScopeForOwnerYankEndpoint() {
        for (String prefix : List.of("/api/v1", "/api/web")) {
            String path = prefix + "/skills/global/demo-skill/versions/1.2.3/yank";
            var denied = registry.authorizeApiToken("POST", path, Set.of("skill:read", "skill:delete"));
            var allowed = registry.authorizeApiToken("POST", path, Set.of("skill:manage"));

            assertFalse(denied.allowed(), path);
            assertEquals("skill:manage", denied.requiredScope(), path);
            assertTrue(allowed.allowed(), path);
        }
    }

    @Test
    void authorizeApiToken_keepsAdminYankSessionOnly() {
        assertFalse(registry.authorizeApiToken("POST", "/api/v1/admin/skills/versions/42/yank", ALL_SCOPES).allowed());
    }

    @Test
    void authorizeApiToken_requiresPublishScopeForOwnerLifecycleEndpoints() {
        for (String prefix : List.of("/api/v1", "/api/web")) {
            for (String path : List.of(
                    prefix + "/skills/global/demo-skill/versions/1.2.3/withdraw-review",
                    prefix + "/skills/global/demo-skill/versions/1.2.3/rerelease",
                    prefix + "/skills/global/demo-skill/submit-review",
                    prefix + "/skills/global/demo-skill/confirm-publish")) {
                var denied = registry.authorizeApiToken("POST", path, Set.of("skill:read", "skill:delete"));
                var allowed = registry.authorizeApiToken("POST", path, PUBLISH_CAPABILITIES);

                assertFalse(denied.allowed(), path);
                String expected = path.endsWith("confirm-publish") ? "skill:publish-private"
                        : path.endsWith("withdraw-review") ? "skill:manage" : "skill:submit";
                assertEquals(expected, denied.requiredScope(), path);
                assertTrue(allowed.allowed(), path);
            }
        }
    }

    @Test
    void authorizeApiToken_keepsWholeSkillWebDeleteSessionOnlyWhileAllowingVersionDelete() {
        assertFalse(registry.authorizeApiToken("DELETE", "/api/web/skills/global/demo-skill", ALL_SCOPES).allowed());
        assertTrue(registry.authorizeApiToken(
                "DELETE", "/api/web/skills/global/demo-skill/versions/1.2.3", ALL_SCOPES).allowed());
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
        assertTrue(registry.authorizeApiToken("POST", "/api/cli/v1/skills/global/publish", Set.of("skill:submit")).allowed());
        assertFalse(registry.authorizeApiToken("POST", "/api/cli/v1/skills/global/publish/validate", Set.of()).allowed());
        assertTrue(registry.authorizeApiToken(
                "POST", "/api/cli/v1/skills/global/publish/validate", Set.of("skill:submit")).allowed());
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
    void suiteBundleWritesRequireAuthenticationAndPublishTokenScope() {
        for (String path : List.of(
                "/api/v1/suite-bundles/preview",
                "/api/v1/suite-bundles/previews/token/confirm",
                "/api/v1/suite-bundles/operations/operation/cancel",
                "/api/v1/suite-bundles/operations/operation/retry",
                "/api/web/suite-bundles/preview",
                "/api/web/suite-bundles/previews/token/confirm",
                "/api/web/suite-bundles/operations/operation/cancel",
                "/api/web/suite-bundles/operations/operation/retry")) {
            assertEquals(RouteSecurityPolicyRegistry.AccessLevel.AUTHENTICATED,
                    registry.accessLevel("POST", path));
            assertTrue(registry.authorizeApiToken("POST", path, Set.of("suite:legacy-write")).allowed());
            assertFalse(registry.authorizeApiToken("POST", path, Set.of("skill:read")).allowed());
        }
        for (String path : List.of(
                "/api/v1/suite-bundles/operations/operation",
                "/api/web/suite-bundles/operations/operation")) {
            assertEquals(RouteSecurityPolicyRegistry.AccessLevel.AUTHENTICATED,
                    registry.accessLevel("GET", path));
            assertTrue(registry.authorizeApiToken("GET", path, Set.of("suite:legacy-write")).allowed());
            assertFalse(registry.authorizeApiToken("GET", path, Set.of("skill:read")).allowed());
        }
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
