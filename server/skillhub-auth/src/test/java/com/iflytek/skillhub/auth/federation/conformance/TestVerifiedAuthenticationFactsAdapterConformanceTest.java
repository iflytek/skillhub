package com.iflytek.skillhub.auth.federation.conformance;

import com.iflytek.skillhub.auth.federation.core.Assurance;
import com.iflytek.skillhub.auth.federation.core.AuthenticationAdapterException;
import com.iflytek.skillhub.auth.federation.core.AuthenticationAdapterFailureReason;
import com.iflytek.skillhub.auth.federation.core.IdentityAssertion;
import com.iflytek.skillhub.auth.federation.core.SubjectRef;
import com.iflytek.skillhub.auth.federation.core.SubjectType;
import com.iflytek.skillhub.auth.federation.core.VerifiedAuthenticationFactsAdapter;
import com.iflytek.skillhub.auth.federation.core.VerifiedEmail;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

class TestVerifiedAuthenticationFactsAdapterConformanceTest
        extends AuthenticationAdapterConformance<TestVerifiedAuthenticationFactsAdapterConformanceTest.TestFacts> {

    private static final URI ISSUER = URI.create("https://test-idp.example/issuer");
    private static final Instant AUTHENTICATED_AT = Instant.parse("2026-09-07T15:10:00Z");

    private final VerifiedAuthenticationFactsAdapter<TestFacts> adapter = facts -> {
        if (facts.subject().isBlank()) {
            throw new AuthenticationAdapterException(AuthenticationAdapterFailureReason.INVALID_ASSERTION);
        }
        return new IdentityAssertion(
                Optional.of("org_test"),
                "connection_test",
                ISSUER,
                new SubjectRef(new SubjectType("test-subject"), facts.subject()),
                facts.emailVerified()
                        ? Optional.of(new VerifiedEmail(facts.email()))
                        : Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                facts.emailVerified()
                        ? Set.of(new Assurance("email_verified"))
                        : Set.of(),
                AUTHENTICATED_AT,
                Map.of()
        );
    };

    @Override
    protected VerifiedAuthenticationFactsAdapter<TestFacts> adapter() {
        return adapter;
    }

    @Override
    protected TestFacts validFacts() {
        return new TestFacts("fake-subject-1", "fake@example.com", true, null);
    }

    @Override
    protected TestFacts factsWithoutVerifiedEmail() {
        return new TestFacts("fake-subject-1", "unverified@example.com", false, null);
    }

    @Override
    protected TestFacts factsContainingSecret(String secret) {
        return new TestFacts("fake-subject-1", "fake@example.com", true, secret);
    }

    @Override
    protected TestFacts invalidFactsContainingSecret(String secret) {
        return new TestFacts(" ", "fake@example.com", true, secret);
    }

    @Override
    protected ExpectedIdentity expectedIdentity() {
        return new ExpectedIdentity(
                "connection_test",
                ISSUER,
                "test-subject",
                "fake-subject-1",
                "fake@example.com",
                "email_verified"
        );
    }

    record TestFacts(String subject, String email, boolean emailVerified, String secret) {
    }
}
