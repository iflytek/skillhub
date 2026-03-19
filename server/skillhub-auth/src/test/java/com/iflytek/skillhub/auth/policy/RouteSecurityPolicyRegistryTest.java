package com.iflytek.skillhub.auth.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

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
    void shouldIgnoreCsrf_forBearerAndApiPaths() {
        assertTrue(registry.shouldIgnoreCsrf("/api/v1/admin/users", null));
        assertTrue(registry.shouldIgnoreCsrf("/not-api", "Bearer token"));
        assertFalse(registry.shouldIgnoreCsrf("/ui/settings", null));
    }

    @Test
    void shouldProjectRequestContext_onlyForApiRoutes() {
        assertTrue(registry.shouldProjectRequestContext("/api/web/namespaces/team-a"));
        assertFalse(registry.shouldProjectRequestContext("/assets/index.css"));
    }
}
