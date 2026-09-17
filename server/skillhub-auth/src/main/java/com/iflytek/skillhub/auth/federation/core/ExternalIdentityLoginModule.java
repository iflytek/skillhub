package com.iflytek.skillhub.auth.federation.core;

import java.util.List;
import java.util.Objects;

/**
 * Applies the deterministic, fail-closed identity correlation order.
 *
 * <p>This module returns a decision only. Transactional binding, provisioning, guards and session
 * creation consume that decision in later orchestration stages.</p>
 */
public final class ExternalIdentityLoginModule {

    private final IdentityCoreActivation activation;
    private final IdentityCorrelationCandidates candidates;
    private final IdentityCorrelationPolicy policy;

    public ExternalIdentityLoginModule(
            IdentityCoreActivation activation,
            IdentityCorrelationCandidates candidates,
            IdentityCorrelationPolicy policy
    ) {
        this.activation = Objects.requireNonNull(activation, "activation must not be null");
        this.candidates = Objects.requireNonNull(candidates, "candidates must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    public IdentityLoginResolution resolve(IdentityAssertion assertion) {
        Objects.requireNonNull(assertion, "assertion must not be null");
        IdentityCoreMode mode = Objects.requireNonNull(
                activation.modeFor(assertion.organizationId()),
                "identity core mode must not be null"
        );
        if (mode == IdentityCoreMode.LEGACY) {
            throw new IdentityCoreDisabledException();
        }

        ExternalIdentityCoordinate coordinate = ExternalIdentityCoordinate.from(assertion);
        IdentityLoginResolution resolved = resolveStage(
                IdentityCorrelationStage.EXACT_BINDING,
                candidates.findExactBinding(coordinate)
        );
        if (resolved != null) {
            return resolved;
        }

        if (coordinate.organizationId().isPresent()) {
            resolved = resolveStage(
                    IdentityCorrelationStage.PRE_PROVISIONED_SUBJECT,
                    candidates.findPreProvisionedSubject(coordinate)
            );
            if (resolved != null) {
                return resolved;
            }
        }

        if (coordinate.organizationId().isPresent()
                && assertion.email().isPresent()
                && policy.allowsVerifiedEmailCorrelation(assertion)) {
            resolved = resolveStage(
                    IdentityCorrelationStage.VERIFIED_EMAIL,
                    candidates.findByVerifiedEmail(
                            coordinate.organizationId().orElseThrow(),
                            assertion.email().orElseThrow()
                    )
            );
            if (resolved != null) {
                return resolved;
            }
        }

        if (policy.allowsJitProvisioning(assertion)) {
            return new IdentityLoginResolution.ProvisionNew();
        }
        return new IdentityLoginResolution.Denied(IdentityLoginDenialReason.NO_SAFE_MATCH);
    }

    private IdentityLoginResolution resolveStage(
            IdentityCorrelationStage stage,
            List<IdentityCorrelationTarget> suppliedCandidates
    ) {
        Objects.requireNonNull(suppliedCandidates, "correlation candidates must not be null");
        if (suppliedCandidates.stream().anyMatch(Objects::isNull)) {
            throw new IllegalStateException("correlation candidates must not contain null values");
        }
        List<IdentityCorrelationTarget> stageCandidates = List.copyOf(suppliedCandidates);
        if (stageCandidates.size() == 1) {
            return new IdentityLoginResolution.Matched(stage, stageCandidates.getFirst());
        }
        if (stageCandidates.size() > 1) {
            return new IdentityLoginResolution.Conflict(stage, stageCandidates.size());
        }
        return null;
    }
}
