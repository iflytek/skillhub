package com.iflytek.skillhub.auth.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.token.ApiTokenScopeService;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiTokenCapabilityCatalogTest {

    private final RouteSecurityPolicyRegistry registry = new RouteSecurityPolicyRegistry();
    private final ApiTokenScopeService scopes = new ApiTokenScopeService(new ObjectMapper(), registry);
    private final AntPathMatcher matcher = new AntPathMatcher();

    @Test
    void subscriptionWritesAreAuthenticatedAndDoNotMatchTheHardDeleteRoleRule() {
        for (String prefix : List.of("/api/v1", "/api/web")) {
            for (String method : List.of("PUT", "DELETE")) {
                String path = prefix + "/skills/42/subscription";
                assertEquals(RouteSecurityPolicyRegistry.AccessLevel.AUTHENTICATED,
                        registry.accessLevel(method, path));
                assertTrue(scopes.authorize(method, path, Set.of("skill:read")).allowed());
                assertEquals("skill:read", scopes.authorize(method, path, Set.of("skill:delete")).requiredScope());
            }
        }
    }

    @Test
    void everyPolicyHasAnExplicitD2ClassificationAndDeleteRequirementsAreUnchanged() throws Exception {
        Map<String, Policy> baseline = baseline().stream().collect(Collectors.toMap(Policy::key, Function.identity()));
        Set<String> additions = additions();
        Set<String> seen = new HashSet<>();
        for (var policy : registry.apiTokenPolicies()) {
            String key = RouteSecurityPolicyRegistry.routeKey(policy.method(), policy.pattern());
            assertTrue(seen.add(key), "Duplicate policy: " + key);
            Policy original = baseline.get(key);
            if (original == null) {
                assertTrue(additions.contains(key), "Unclassified new policy: " + key);
                assertEquals("skill:read", policy.requiredScope(), key);
            } else {
                String expected = "skill:publish".equals(original.scope())
                        ? replacement(original) : original.scope();
                assertEquals(expected, policy.requiredScope(), key);
            }
        }
        Set<String> expected = new HashSet<>(baseline.keySet());
        expected.addAll(additions);
        assertEquals(expected, seen, "No original route may disappear or unclassified route be added");
    }

    @Test
    void pairedWebAndV1RoutesRequireTheSameCapability() {
        Map<String, String> requirements = registry.apiTokenPolicies().stream()
                .filter(policy -> policy.requiredScope() != null)
                .collect(Collectors.toMap(
                        policy -> RouteSecurityPolicyRegistry.routeKey(policy.method(), policy.pattern()),
                        RouteSecurityPolicyRegistry.ApiTokenPolicy::requiredScope));
        requirements.forEach((key, scope) -> {
            if (key.contains("/api/web/")) {
                assertEquals(scope, requirements.get(key.replace("/api/web/", "/api/v1/")), key);
            }
        });
    }

    @Test
    void allLegacyScopeCombinationsPreserveTheAllowSetExceptTheEightD2Additions() throws Exception {
        List<Policy> baseline = baseline();
        List<String> oldScopes = List.of("skill:read", "skill:publish", "skill:delete", "token:manage");
        Set<String> requests = new HashSet<>(additions());
        for (Policy policy : baseline) {
            for (String method : List.of("GET", "POST", "PUT", "DELETE", "PATCH")) {
                requests.add(method + " " + sample(policy.path()));
                requests.add(method + " " + sample(policy.path()).replace("sample", "subscription"));
            }
        }
        requests.add("POST /api/v1/skills/new/unclassified");
        for (int mask = 0; mask < 16; mask++) {
            Set<String> stored = new HashSet<>();
            for (int bit = 0; bit < oldScopes.size(); bit++) {
                if ((mask & (1 << bit)) != 0) {
                    stored.add(oldScopes.get(bit));
                }
            }
            for (String request : requests) {
                String[] parts = request.split(" ", 2);
                String path = sample(parts[1]);
                boolean before = oldAllowed(baseline, parts[0], path, stored);
                boolean after = scopes.authorize(parts[0], path, stored).allowed();
                if (additions().stream().anyMatch(key -> matchesRequest(key, parts[0], path))) {
                    assertEquals(stored.contains("skill:read"), after, request + " " + stored);
                } else {
                    assertEquals(before, after, request + " " + stored);
                }
            }
        }
    }

    @Test
    void candidateCapabilitiesAllowSubmissionButNotConfirmationManagementDeletionOrSuiteWrites() throws Exception {
        Set<String> candidate = Set.of("skill:read", "skill:submit");
        for (Policy original : baseline()) {
            if (!"skill:publish".equals(original.scope()) && !"skill:delete".equals(original.scope())) {
                continue;
            }
            String capability = "skill:publish".equals(original.scope()) ? replacement(original) : "skill:delete";
            String path = sample(original.path());
            var decision = scopes.authorize(original.method(), path, candidate);
            assertEquals("skill:submit".equals(capability), decision.allowed(), original.key());
            if (!decision.allowed()) {
                assertEquals(capability, decision.requiredScope(), original.key());
            }
            assertTrue(scopes.authorize(original.method(), path, Set.of(capability)).allowed(), original.key());
            assertFalse(scopes.authorize(original.method(), path, Set.of()).allowed(), original.key());
        }
    }

    private String replacement(Policy original) {
        String path = original.path();
        if (path.contains("/suites") || path.contains("/suite-bundles/")) {
            return "suite:legacy-write";
        }
        if (path.endsWith("/confirm-publish")) {
            return "skill:publish-private";
        }
        if (path.endsWith("/visibility") || path.endsWith("/archive") || path.endsWith("/unarchive")
                || path.endsWith("/yank") || path.endsWith("/withdraw-review") || path.contains("/labels/")
                || path.endsWith("/security-audit/retry")) {
            return "skill:manage";
        }
        assertTrue(path.endsWith("/publish") || path.endsWith("/publish/validate")
                || path.endsWith("/package-preview") || path.endsWith("/submit-review")
                || path.endsWith("/rerelease") || path.equals("/api/v1/skills"), original.key());
        return "skill:submit";
    }

    private Set<String> additions() {
        Set<String> keys = new HashSet<>();
        for (String prefix : List.of("/api/v1", "/api/web")) {
            keys.add("GET " + prefix + "/me/namespaces");
            keys.add("GET " + prefix + "/me/namespaces/page");
            keys.add("PUT " + prefix + "/skills/*/subscription");
            keys.add("DELETE " + prefix + "/skills/*/subscription");
        }
        return keys;
    }

    private boolean oldAllowed(List<Policy> baseline, String method, String path, Set<String> stored) {
        for (Policy policy : baseline) {
            if ((policy.method().equals("ANY") || policy.method().equals(method))
                    && matcher.match(policy.path(), path)) {
                return policy.scope() == null || stored.contains(policy.scope());
            }
        }
        return !path.startsWith("/api/");
    }

    private boolean matchesRequest(String key, String method, String path) {
        String[] parts = key.split(" ", 2);
        return parts[0].equals(method) && matcher.match(parts[1], path);
    }

    private String sample(String pattern) {
        return pattern.replace("/**", "/sample/leaf").replace("*", "sample");
    }

    private List<Policy> baseline() throws Exception {
        var resource = getClass().getResourceAsStream("/api-token-policies-a3dc2c4a.txt");
        assertNotNull(resource);
        List<Policy> result = new ArrayList<>();
        try (var reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            for (String line : reader.lines().filter(value -> !value.startsWith("#")).toList()) {
                String[] parts = line.split(" ");
                result.add(new Policy(parts[0], parts[1], parts[2].equals("-") ? null : parts[2]));
            }
        }
        return result;
    }

    private record Policy(String method, String path, String scope) {
        String key() {
            return method + " " + path;
        }
    }
}
