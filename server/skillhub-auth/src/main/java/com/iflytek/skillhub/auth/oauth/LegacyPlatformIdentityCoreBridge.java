package com.iflytek.skillhub.auth.oauth;

import com.iflytek.skillhub.auth.federation.core.ExternalIdentityCoordinate;
import com.iflytek.skillhub.auth.federation.core.ExternalIdentityLoginModule;
import com.iflytek.skillhub.auth.federation.core.IdentityAssertion;
import com.iflytek.skillhub.auth.federation.core.IdentityCoreActivation;
import com.iflytek.skillhub.auth.federation.core.IdentityCoreMode;
import com.iflytek.skillhub.auth.federation.core.IdentityCorrelationCandidates;
import com.iflytek.skillhub.auth.federation.core.IdentityCorrelationPolicy;
import com.iflytek.skillhub.auth.federation.core.IdentityCorrelationStage;
import com.iflytek.skillhub.auth.federation.core.IdentityCorrelationTarget;
import com.iflytek.skillhub.auth.federation.core.IdentityLoginResolution;
import com.iflytek.skillhub.auth.federation.core.VerifiedAuthenticationFactsAdapter;
import com.iflytek.skillhub.auth.federation.core.VerifiedEmail;
import com.iflytek.skillhub.auth.federation.migration.LegacyIdentityBindingDualReader;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Adapts legacy platform bindings to the unified correlation core while legacy persistence remains
 * authoritative. SHADOW failures are observable but cannot affect the existing login outcome.
 */
@Service
public final class LegacyPlatformIdentityCoreBridge implements LegacyPlatformIdentityCore {

    private static final Logger log = LoggerFactory.getLogger(LegacyPlatformIdentityCoreBridge.class);

    private final IdentityCoreActivation activation;
    private final LegacyIdentityBindingDualReader bindings;
    private final VerifiedAuthenticationFactsAdapter<OAuthClaims> assertionAdapter;

    public LegacyPlatformIdentityCoreBridge(
            IdentityCoreActivation activation,
            LegacyIdentityBindingDualReader bindings,
            VerifiedAuthenticationFactsAdapter<OAuthClaims> assertionAdapter
    ) {
        this.activation = Objects.requireNonNull(activation, "activation");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.assertionAdapter = Objects.requireNonNull(assertionAdapter, "assertionAdapter");
    }

    @Override
    public LegacyPlatformIdentityDecision evaluate(OAuthClaims claims) {
        IdentityCoreMode mode = Objects.requireNonNull(
                activation.modeFor(Optional.empty()),
                "identity core mode"
        );
        if (mode == IdentityCoreMode.LEGACY) {
            return LegacyPlatformIdentityDecision.legacy();
        }

        try {
            IdentityAssertion assertion = assertionAdapter.toAssertion(claims);
            IdentityLoginResolution resolution = module(mode, claims, assertion).resolve(assertion);
            requireCompatibleLegacyResolution(resolution);
            log.debug(
                    "Unified identity {} evaluation completed for legacy platform connection '{}' with {}",
                    mode,
                    assertion.connectionId(),
                    resolution.getClass().getSimpleName()
            );
            return LegacyPlatformIdentityDecision.evaluated(mode, resolution);
        } catch (RuntimeException failure) {
            if (mode == IdentityCoreMode.SHADOW) {
                log.warn(
                        "Unified identity SHADOW evaluation failed for legacy provider '{}', category '{}'; legacy login continues",
                        safeProvider(claims),
                        failure.getClass().getSimpleName()
                );
                return new LegacyPlatformIdentityDecision(IdentityCoreMode.SHADOW, Optional.empty());
            }
            throw new OAuthIdentityCoreException(failure);
        }
    }

    private ExternalIdentityLoginModule module(
            IdentityCoreMode mode,
            OAuthClaims claims,
            IdentityAssertion assertion
    ) {
        ExternalIdentityCoordinate expected = ExternalIdentityCoordinate.from(assertion);
        IdentityCorrelationCandidates candidates = new IdentityCorrelationCandidates() {
            @Override
            public List<IdentityCorrelationTarget> findExactBinding(ExternalIdentityCoordinate coordinate) {
                if (!expected.equals(coordinate)) {
                    return List.of();
                }
                return bindings.findExactBinding(
                        mode,
                        coordinate,
                        claims.provider(),
                        claims.subject()
                );
            }

            /**
             * Deliberately empty on the legacy platform path: pre-provisioned subjects are an
             * enterprise control-plane concept, and this batch must not introduce a correlation
             * path that could match an account the legacy flow would not have matched.
             */
            @Override
            public List<IdentityCorrelationTarget> findPreProvisionedSubject(
                    ExternalIdentityCoordinate coordinate
            ) {
                return List.of();
            }

            /** Deliberately empty; see {@link #allowsVerifiedEmailCorrelation}. */
            @Override
            public List<IdentityCorrelationTarget> findByVerifiedEmail(
                    String organizationId,
                    VerifiedEmail email
            ) {
                return List.of();
            }
        };
        IdentityCorrelationPolicy policy = new IdentityCorrelationPolicy() {
            /**
             * Disabled for public OAuth in this batch. Verified-email correlation can join an
             * external login to an existing account, so enabling it here would change matching
             * results while legacy {@code identity_binding} is still the write authority. It
             * belongs to the batch that also owns the enterprise correlation policy.
             */
            @Override
            public boolean allowsVerifiedEmailCorrelation(IdentityAssertion ignored) {
                return false;
            }

            @Override
            public boolean allowsJitProvisioning(IdentityAssertion ignored) {
                return true;
            }
        };
        return new ExternalIdentityLoginModule(ignored -> mode, candidates, policy);
    }

    private static void requireCompatibleLegacyResolution(IdentityLoginResolution resolution) {
        if (resolution instanceof IdentityLoginResolution.ProvisionNew) {
            return;
        }
        if (resolution instanceof IdentityLoginResolution.Matched matched
                && matched.stage() == IdentityCorrelationStage.EXACT_BINDING
                && matched.target().userId().isPresent()) {
            return;
        }
        throw new IllegalStateException(
                "Unified identity result is incompatible with the legacy platform flow"
        );
    }

    private static String safeProvider(OAuthClaims claims) {
        return claims == null || claims.provider() == null ? "unknown" : claims.provider();
    }
}
