package com.iflytek.skillhub.auth.federation.migration;

import com.iflytek.skillhub.auth.entity.IdentityBinding;
import com.iflytek.skillhub.auth.federation.association.ExternalIdentity;
import com.iflytek.skillhub.auth.federation.association.ExternalIdentityRepository;
import com.iflytek.skillhub.auth.federation.core.ExternalIdentityCoordinate;
import com.iflytek.skillhub.auth.federation.core.IdentityCoreMode;
import com.iflytek.skillhub.auth.federation.core.IdentityCorrelationTarget;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** V2-first read seam for legacy platform bindings, with an immediate legacy rollback mode. */
public final class LegacyIdentityBindingDualReader {

    private final ExternalIdentityRepository externalIdentities;
    private final IdentityBindingRepository legacyBindings;
    private final IdentityBindingReadMetrics metrics;

    public LegacyIdentityBindingDualReader(
            ExternalIdentityRepository externalIdentities,
            IdentityBindingRepository legacyBindings,
            IdentityBindingReadMetrics metrics
    ) {
        this.externalIdentities = Objects.requireNonNull(externalIdentities, "externalIdentities");
        this.legacyBindings = Objects.requireNonNull(legacyBindings, "legacyBindings");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public List<IdentityCorrelationTarget> findExactBinding(
            IdentityCoreMode mode,
            ExternalIdentityCoordinate coordinate,
            String provider,
            String subject
    ) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(coordinate, "coordinate");
        if (mode == IdentityCoreMode.LEGACY) {
            return legacyOnly(mode, provider, subject);
        }

        Optional<ExternalIdentity> v2 = externalIdentities.findByCoordinate(coordinate);
        Optional<IdentityBinding> legacy = legacyBindings.findByProviderCodeAndSubject(provider, subject);
        if (v2.isEmpty()) {
            metrics.record(mode, legacy.isPresent()
                    ? IdentityBindingReadOutcome.V2_MISS_LEGACY_FALLBACK
                    : IdentityBindingReadOutcome.LEGACY_MISS);
            return target(legacy.map(IdentityBinding::getUserId));
        }

        ExternalIdentity identity = v2.orElseThrow();
        if (!identity.isActive()) {
            metrics.record(mode, IdentityBindingReadOutcome.V2_UNAVAILABLE);
            if (mode == IdentityCoreMode.SHADOW) {
                return target(legacy.map(IdentityBinding::getUserId));
            }
            identity.requireActive();
        }

        Optional<String> legacyUserId = legacy.map(IdentityBinding::getUserId);
        if (legacyUserId.isEmpty()) {
            metrics.record(mode, IdentityBindingReadOutcome.V2_ONLY);
            if (mode == IdentityCoreMode.ACTIVE) {
                throw new IllegalStateException(
                        "Legacy platform identity exists only in V2 while legacy writes remain enabled"
                );
            }
            return List.of();
        }
        if (!identity.getUserId().equals(legacyUserId.orElseThrow())) {
            metrics.record(mode, IdentityBindingReadOutcome.MISMATCH);
            if (mode == IdentityCoreMode.ACTIVE) {
                throw new IllegalStateException("V2 and legacy identity bindings resolve different accounts");
            }
            return target(legacyUserId);
        }

        metrics.record(mode, IdentityBindingReadOutcome.MATCH);
        return target(Optional.of(mode == IdentityCoreMode.ACTIVE
                ? identity.getUserId()
                : legacyUserId.orElseThrow()));
    }

    private List<IdentityCorrelationTarget> legacyOnly(
            IdentityCoreMode mode,
            String provider,
            String subject
    ) {
        Optional<String> userId = legacyBindings.findByProviderCodeAndSubject(provider, subject)
                .map(IdentityBinding::getUserId);
        metrics.record(mode, userId.isPresent()
                ? IdentityBindingReadOutcome.LEGACY_HIT
                : IdentityBindingReadOutcome.LEGACY_MISS);
        return target(userId);
    }

    private static List<IdentityCorrelationTarget> target(Optional<String> userId) {
        return userId.map(value -> List.of(IdentityCorrelationTarget.account(value)))
                .orElseGet(List::of);
    }
}
