package com.iflytek.skillhub.auth.federation.adapter;

import com.iflytek.skillhub.auth.federation.conformance.AuthenticationAdapterConformance;
import com.iflytek.skillhub.auth.federation.core.VerifiedAuthenticationFactsAdapter;
import com.iflytek.skillhub.auth.oauth.OAuthClaims;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

class LegacyOAuthVerifiedFactsAdapterConformanceTest
        extends AuthenticationAdapterConformance<OAuthClaims> {

    private static final Instant NOW = Instant.parse("2026-09-07T15:00:00Z");

    private final LegacyOAuthVerifiedFactsAdapter adapter = new LegacyOAuthVerifiedFactsAdapter(
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Override
    protected VerifiedAuthenticationFactsAdapter<OAuthClaims> adapter() {
        return adapter;
    }

    @Override
    protected OAuthClaims validFacts() {
        return new OAuthClaims(
                "github",
                "subject-1",
                "Alice@Example.com",
                true,
                "alice",
                Map.of("iss", "https://github.example/issuer")
        );
    }

    @Override
    protected OAuthClaims factsWithoutVerifiedEmail() {
        return new OAuthClaims(
                "github",
                "subject-1",
                "unverified@example.com",
                false,
                "alice",
                Map.of("iss", "https://github.example/issuer")
        );
    }

    @Override
    protected OAuthClaims factsContainingSecret(String secret) {
        return new OAuthClaims(
                "github",
                "subject-1",
                "alice@example.com",
                true,
                "alice",
                Map.of(
                        "iss", "https://github.example/issuer",
                        "access_token", secret,
                        "client_secret", secret
                )
        );
    }

    @Override
    protected OAuthClaims invalidFactsContainingSecret(String secret) {
        return new OAuthClaims(
                secret + ":",
                "subject-1",
                "alice@example.com",
                true,
                "alice",
                Map.of("access_token", secret)
        );
    }

    @Override
    protected ExpectedIdentity expectedIdentity() {
        return new ExpectedIdentity(
                LegacyOAuthIdentityCoordinate.connectionId("github"),
                URI.create("urn:skillhub:legacy-oauth"),
                "legacy-oauth-subject",
                "subject-1",
                "alice@example.com",
                "email_verified"
        );
    }
}
