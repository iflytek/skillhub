package com.iflytek.skillhub.auth.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Maintainer #1069 section 3 combination matrix, expressed with the token scopes current main ships.
 *
 * <p>Rows that need #1078's split capabilities (read+submit without publish-private) cannot be
 * expressed yet: today {@code skill:publish} covers both submission and PRIVATE confirmation.
 * These tests pin the route-policy half; the domain half is covered by publish/approval tests.
 */
class TeamAutoPublicationTokenMatrixTest {

    /** The single route-policy capability that currently means "may submit". */
    private static final String SUBMIT_CAPABILITY = "skill:publish";

    private final RouteSecurityPolicyRegistry registry = new RouteSecurityPolicyRegistry();

    @Test
    void readOnlyTokenCannotUploadEvenIfMemberIsGranted() {
        for (String route : uploadRoutes()) {
            var decision = registry.authorizeApiToken("POST", route, Set.of("skill:read"));
            assertFalse(decision.allowed(), route);
            assertEquals(SUBMIT_CAPABILITY, decision.requiredScope(), route);
        }
    }

    @Test
    void submitCapableTokenReachesTheSameUploadPolicyAsBrowserSubmission() {
        for (String route : uploadRoutes()) {
            assertTrue(registry.authorizeApiToken("POST", route, Set.of("skill:read", SUBMIT_CAPABILITY)).allowed(),
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
                Set.of(SUBMIT_CAPABILITY)).allowed());
    }

    private static List<String> uploadRoutes() {
        return List.of("/api/v1/skills/team/publish", "/api/web/skills/team/publish",
                "/api/cli/v1/skills/team/publish", "/api/v1/publish");
    }
}
