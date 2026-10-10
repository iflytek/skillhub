package com.iflytek.skillhub.auth.federation.adapter;

import com.iflytek.skillhub.auth.federation.core.Assurance;
import com.iflytek.skillhub.auth.federation.core.IdentityAssertion;
import com.iflytek.skillhub.auth.oauth.OAuthClaims;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyOAuthVerifiedFactsAdapterTest {

    private static final Instant NOW = Instant.parse("2026-09-07T14:00:00Z");

    private final LegacyOAuthVerifiedFactsAdapter adapter = new LegacyOAuthVerifiedFactsAdapter(
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void mapsPublicOAuthClaimsToPlatformScopedLegacyConnection() {
        OAuthClaims claims = new OAuthClaims(
                "github",
                "subject-1",
                "Alice@Example.com",
                true,
                "alice",
                Map.of("avatar_url", "https://avatars.example/alice.png")
        );

        IdentityAssertion assertion = adapter.toAssertion(claims);

        assertThat(assertion.organizationId()).isEmpty();
        assertThat(assertion.connectionId())
                .isEqualTo(LegacyOAuthIdentityCoordinate.connectionId("github"));
        assertThat(assertion.issuer()).isEqualTo(URI.create("urn:skillhub:legacy-oauth"));
        assertThat(assertion.subject().type().value()).isEqualTo("legacy-oauth-subject");
        assertThat(assertion.subject().value()).isEqualTo("subject-1");
        assertThat(assertion.email()).get().extracting("value").isEqualTo("alice@example.com");
        assertThat(assertion.loginName()).contains("alice");
        assertThat(assertion.displayName()).contains("alice");
        assertThat(assertion.avatarUrl()).contains("https://avatars.example/alice.png");
        assertThat(assertion.assurance()).containsExactly(new Assurance("email_verified"));
        assertThat(assertion.authenticatedAt()).isEqualTo(NOW);
        assertThat(assertion.attributes()).isEmpty();
    }

    @Test
    void keepsLegacyIssuerStableWithoutForwardingRawClaims() {
        OAuthClaims claims = new OAuthClaims(
                "corp-oidc",
                "subject-2",
                null,
                false,
                "bob",
                Map.of(
                        "iss", "https://idp.example/tenant",
                        "access_token", "must-not-cross-core",
                        "groups", java.util.List.of("admins")
                )
        );

        IdentityAssertion assertion = adapter.toAssertion(claims);

        assertThat(assertion.issuer()).isEqualTo(URI.create("urn:skillhub:legacy-oauth"));
        assertThat(assertion.email()).isEmpty();
        assertThat(assertion.assurance()).isEmpty();
        assertThat(assertion.attributes()).isEmpty();
        assertThat(assertion.toString()).doesNotContain("must-not-cross-core", "admins");
    }
}
