package com.iflytek.skillhub.auth.federation.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExternalIdentityLoginModuleTest {

    private static final IdentityCorrelationTarget BOUND_TARGET =
            IdentityCorrelationTarget.linked("usr_bound", "member_bound");
    private static final IdentityCorrelationTarget PROVISIONED_TARGET =
            IdentityCorrelationTarget.membership("member_provisioned");
    private static final IdentityCorrelationTarget EMAIL_TARGET =
            IdentityCorrelationTarget.linked("usr_email", "member_email");

    @Test
    void exactBindingWinsWithoutConsultingWeakerCorrelationStages() {
        RecordingCandidates candidates = new RecordingCandidates();
        candidates.exact = List.of(BOUND_TARGET);
        ExternalIdentityLoginModule module = module(candidates, permissivePolicy());

        IdentityLoginResolution resolution = module.resolve(assertion(true));

        assertThat(resolution).isEqualTo(new IdentityLoginResolution.Matched(
                IdentityCorrelationStage.EXACT_BINDING,
                BOUND_TARGET
        ));
        assertThat(candidates.calls).containsExactly("exact");
    }

    @Test
    void preProvisionedImmutableSubjectWinsBeforeVerifiedEmail() {
        RecordingCandidates candidates = new RecordingCandidates();
        candidates.preProvisioned = List.of(PROVISIONED_TARGET);
        ExternalIdentityLoginModule module = module(candidates, permissivePolicy());

        IdentityLoginResolution resolution = module.resolve(assertion(true));

        assertThat(resolution).isEqualTo(new IdentityLoginResolution.Matched(
                IdentityCorrelationStage.PRE_PROVISIONED_SUBJECT,
                PROVISIONED_TARGET
        ));
        assertThat(candidates.calls).containsExactly("exact", "pre-provisioned");
    }

    @Test
    void verifiedEmailIsUsedOnlyAfterStrongerStagesMissAndPolicyAllowsIt() {
        RecordingCandidates candidates = new RecordingCandidates();
        candidates.verifiedEmail = List.of(EMAIL_TARGET);
        ExternalIdentityLoginModule module = module(candidates, permissivePolicy());

        IdentityLoginResolution resolution = module.resolve(assertion(true));

        assertThat(resolution).isEqualTo(new IdentityLoginResolution.Matched(
                IdentityCorrelationStage.VERIFIED_EMAIL,
                EMAIL_TARGET
        ));
        assertThat(candidates.calls).containsExactly("exact", "pre-provisioned", "verified-email");
    }

    @Test
    void ambiguousCandidatesStopAtTheStageThatDetectedTheConflict() {
        RecordingCandidates candidates = new RecordingCandidates();
        candidates.verifiedEmail = List.of(
                EMAIL_TARGET,
                IdentityCorrelationTarget.linked("usr_other", "member_other")
        );
        ExternalIdentityLoginModule module = module(candidates, permissivePolicy());

        IdentityLoginResolution resolution = module.resolve(assertion(true));

        assertThat(resolution).isEqualTo(new IdentityLoginResolution.Conflict(
                IdentityCorrelationStage.VERIFIED_EMAIL,
                2
        ));
        assertThat(candidates.calls).containsExactly("exact", "pre-provisioned", "verified-email");
    }

    @Test
    void noSafeCandidateIsDeniedWhenJitProvisioningIsNotAllowed() {
        RecordingCandidates candidates = new RecordingCandidates();
        IdentityCorrelationPolicy policy = policy(true, false);
        ExternalIdentityLoginModule module = module(candidates, policy);

        IdentityLoginResolution resolution = module.resolve(assertion(true));

        assertThat(resolution).isEqualTo(new IdentityLoginResolution.Denied(
                IdentityLoginDenialReason.NO_SAFE_MATCH
        ));
    }

    @Test
    void noSafeCandidateRequestsProvisioningOnlyWhenPolicyExplicitlyAllowsIt() {
        RecordingCandidates candidates = new RecordingCandidates();
        ExternalIdentityLoginModule module = module(candidates, permissivePolicy());

        IdentityLoginResolution resolution = module.resolve(assertion(true));

        assertThat(resolution).isEqualTo(new IdentityLoginResolution.ProvisionNew());
    }

    @Test
    void absentVerifiedEmailSkipsEmailLookupEvenWhenPolicyAllowsCorrelation() {
        RecordingCandidates candidates = new RecordingCandidates();
        ExternalIdentityLoginModule module = module(candidates, policy(true, false));

        IdentityLoginResolution resolution = module.resolve(assertion(false));

        assertThat(resolution).isEqualTo(new IdentityLoginResolution.Denied(
                IdentityLoginDenialReason.NO_SAFE_MATCH
        ));
        assertThat(candidates.calls).containsExactly("exact", "pre-provisioned");
    }

    @Test
    void legacyModeFailsBeforeAnyCorrelationLookup() {
        RecordingCandidates candidates = new RecordingCandidates();
        ExternalIdentityLoginModule module = new ExternalIdentityLoginModule(
                organizationId -> IdentityCoreMode.LEGACY,
                candidates,
                permissivePolicy()
        );

        assertThatThrownBy(() -> module.resolve(assertion(true)))
                .isInstanceOf(IdentityCoreDisabledException.class);
        assertThat(candidates.calls).isEmpty();
    }

    private static ExternalIdentityLoginModule module(
            IdentityCorrelationCandidates candidates,
            IdentityCorrelationPolicy policy
    ) {
        return new ExternalIdentityLoginModule(
                organizationId -> IdentityCoreMode.ACTIVE,
                candidates,
                policy
        );
    }

    private static IdentityCorrelationPolicy permissivePolicy() {
        return policy(true, true);
    }

    private static IdentityCorrelationPolicy policy(boolean email, boolean jit) {
        return new IdentityCorrelationPolicy() {
            @Override
            public boolean allowsVerifiedEmailCorrelation(IdentityAssertion assertion) {
                return email;
            }

            @Override
            public boolean allowsJitProvisioning(IdentityAssertion assertion) {
                return jit;
            }
        };
    }

    private static IdentityAssertion assertion(boolean withVerifiedEmail) {
        return new IdentityAssertion(
                Optional.of("org_1"),
                "connection_1",
                URI.create("https://identity.example.com"),
                new SubjectRef(new SubjectType("opaque-user-id"), "subject-123"),
                withVerifiedEmail
                        ? Optional.of(new VerifiedEmail("alice@example.com"))
                        : Optional.empty(),
                Optional.of("alice"),
                Optional.of("Alice"),
                Optional.empty(),
                Set.of(new Assurance("single-factor")),
                Instant.parse("2026-09-07T12:00:00Z"),
                Map.of()
        );
    }

    private static final class RecordingCandidates implements IdentityCorrelationCandidates {

        private final List<String> calls = new ArrayList<>();
        private List<IdentityCorrelationTarget> exact = List.of();
        private List<IdentityCorrelationTarget> preProvisioned = List.of();
        private List<IdentityCorrelationTarget> verifiedEmail = List.of();

        @Override
        public List<IdentityCorrelationTarget> findExactBinding(ExternalIdentityCoordinate coordinate) {
            calls.add("exact");
            return exact;
        }

        @Override
        public List<IdentityCorrelationTarget> findPreProvisionedSubject(ExternalIdentityCoordinate coordinate) {
            calls.add("pre-provisioned");
            return preProvisioned;
        }

        @Override
        public List<IdentityCorrelationTarget> findByVerifiedEmail(
                String organizationId,
                VerifiedEmail email
        ) {
            calls.add("verified-email");
            return verifiedEmail;
        }
    }
}
