package com.iflytek.skillhub.auth.oauth;

import com.iflytek.skillhub.auth.federation.core.IdentityCoreMode;
import com.iflytek.skillhub.auth.federation.core.IdentityLoginResolution;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of evaluating one platform-scoped legacy OAuth login against the unified identity core.
 *
 * <p>LEGACY mode intentionally carries no resolution because the old binding path remains the only
 * decision-maker. SHADOW and ACTIVE carry the core resolution when evaluation was possible.</p>
 */
public record LegacyPlatformIdentityDecision(
        IdentityCoreMode mode,
        Optional<IdentityLoginResolution> resolution
) {

    public LegacyPlatformIdentityDecision {
        Objects.requireNonNull(mode, "mode must not be null");
        resolution = Objects.requireNonNull(resolution, "resolution must not be null");
        if (mode == IdentityCoreMode.LEGACY && resolution.isPresent()) {
            throw new IllegalArgumentException("LEGACY mode must not carry a unified identity resolution");
        }
    }

    public static LegacyPlatformIdentityDecision legacy() {
        return new LegacyPlatformIdentityDecision(IdentityCoreMode.LEGACY, Optional.empty());
    }

    public static LegacyPlatformIdentityDecision evaluated(
            IdentityCoreMode mode,
            IdentityLoginResolution resolution
    ) {
        if (mode == IdentityCoreMode.LEGACY) {
            throw new IllegalArgumentException("Use legacy() for LEGACY mode");
        }
        return new LegacyPlatformIdentityDecision(
                mode,
                Optional.of(Objects.requireNonNull(resolution, "resolution must not be null"))
        );
    }
}
