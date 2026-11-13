package com.iflytek.skillhub.auth.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Maintainer #1069 section 3 combination matrix at the route-policy layer.
 *
 * <p>The scope that means "may submit" is read from the registry rather than hard-coded: it is
 * {@code skill:publish} before #1078 and {@code skill:submit} once #1078 splits token capabilities.
 * Every upload route must agree on that one scope. The domain half is covered by publish/approval tests.
 */
class TeamAutoPublicationTokenMatrixTest {

    /** Scopes that may legitimately mean "may submit", before and after #1078. */
    private static final Set<String> SUBMIT_CAPABILITIES = Set.of("skill:publish", "skill:submit");

    private final RouteSecurityPolicyRegistry registry = new RouteSecurityPolicyRegistry();

    @Test
    void readOnlyTokenCannotUploadEvenIfMemberIsGranted() {
        String submitCapability = submitCapability();
        for (String route : uploadRoutes()) {
            var decision = registry.authorizeApiToken("POST", route, Set.of("skill:read"));
            assertFalse(decision.allowed(), route);
            assertEquals(submitCapability, decision.requiredScope(), route);
        }
    }

    @Test
    void submitCapableTokenReachesTheSameUploadPolicyAsBrowserSubmission() {
        String submitCapability = submitCapability();
        for (String route : uploadRoutes()) {
            assertTrue(registry.authorizeApiToken("POST", route, Set.of("skill:read", submitCapability)).allowed(),
                    route);
        }
    }

    @Test
    void privateConfirmationAndVisibilityRequestsStillRequireTheirOwnRoutePolicy() {
        for (String prefix : List.of("/api/v1", "/api/web")) {
            assertFalse(registry.authorizeApiToken("POST", prefix + "/skills/team/demo/confirm-publish",
                    Set.of("skill:read")).allowed());
            assertFalse(registry.authorizeApiToken("POST", prefix + "/skills/team/demo/visibility",
                    Set.of("skill:read")).allowed());
        }
    }

    @Test
    void noTokenScopeCanGrantTeamAutoPublication() {
        Set<String> everyKnownScope = Set.of("skill:read", "skill:publish", "skill:delete", "token:manage",
                "skill:submit", "skill:publish-private", "skill:manage");
        for (String prefix : List.of("/api/v1", "/api/web")) {
            assertFalse(registry.authorizeApiToken("PUT",
                    prefix + "/namespaces/team/members/someone/team-auto-publication", everyKnownScope).allowed());
        }
    }

    @Test
    void reviewDecisionsAreNotReachableThroughSubmitCapability() {
        assertFalse(registry.authorizeApiToken("POST", "/api/web/suites/reviews/1/approve",
                Set.of(submitCapability())).allowed());
    }

    /** The scope the registry requires for uploads; it must be one known submit scope on every route. */
    private String submitCapability() {
        String required = registry.authorizeApiToken("POST", uploadRoutes().getFirst(), Set.of("skill:read"))
                .requiredScope();
        assertTrue(SUBMIT_CAPABILITIES.contains(required), "unexpected upload scope " + required);
        return required;
    }

    private static List<String> uploadRoutes() {
        return List.of("/api/v1/skills/team/publish", "/api/web/skills/team/publish",
                "/api/cli/v1/skills/team/publish", "/api/v1/publish");
    }
}
