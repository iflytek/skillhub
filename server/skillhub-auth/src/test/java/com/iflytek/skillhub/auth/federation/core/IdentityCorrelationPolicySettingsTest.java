package com.iflytek.skillhub.auth.federation.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IdentityCorrelationPolicySettingsTest {

    @Test
    void defaultPolicyDisablesEmailCorrelationAndJit() {
        IdentityCorrelationPolicySettings policy = IdentityCorrelationPolicySettings.disabled();

        assertThat(policy.allowsVerifiedEmailCorrelation(assertion(true, true))).isFalse();
        assertThat(policy.allowsJitProvisioning(assertion(true, true))).isFalse();
    }

    @Test
    void verifiedEmailCorrelationRequiresBothTrustedEmailAndAssurance() {
        IdentityCorrelationPolicySettings policy =
                IdentityCorrelationPolicySettings.verifiedEmailOnly();

        assertThat(policy.allowsVerifiedEmailCorrelation(assertion(true, true))).isTrue();
        assertThat(policy.allowsVerifiedEmailCorrelation(assertion(true, false))).isFalse();
        assertThat(policy.allowsVerifiedEmailCorrelation(assertion(false, true))).isFalse();
    }

    @Test
    void jitRequiresEmailCorrelationAndTheSameTrustedSignal() {
        assertThatThrownBy(() -> new IdentityCorrelationPolicySettings(false, true))
                .isInstanceOf(IllegalArgumentException.class);

        IdentityCorrelationPolicySettings policy =
                IdentityCorrelationPolicySettings.verifiedEmailWithJit();
        assertThat(policy.allowsJitProvisioning(assertion(true, true))).isTrue();
        assertThat(policy.allowsJitProvisioning(assertion(true, false))).isFalse();
        assertThat(policy.allowsJitProvisioning(assertion(false, true))).isFalse();
    }

    private static IdentityAssertion assertion(boolean email, boolean assurance) {
        return new IdentityAssertion(
                Optional.of("organization-1"),
                "connection-1",
                URI.create("https://identity.example.com"),
                new SubjectRef(new SubjectType("oidc-sub"), "employee-42"),
                email
                        ? Optional.of(new VerifiedEmail("alice@example.com"))
                        : Optional.empty(),
                Optional.of("alice"),
                Optional.of("Alice Example"),
                Optional.empty(),
                assurance
                        ? Set.of(new Assurance("email_verified"))
                        : Set.of(new Assurance("oidc")),
                Instant.parse("2026-09-08T10:00:00Z"),
                Map.of()
        );
    }
}
