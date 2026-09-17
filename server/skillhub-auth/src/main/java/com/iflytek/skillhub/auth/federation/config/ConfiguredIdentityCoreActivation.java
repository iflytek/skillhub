package com.iflytek.skillhub.auth.federation.config;

import com.iflytek.skillhub.auth.federation.core.IdentityCoreActivation;
import com.iflytek.skillhub.auth.federation.core.IdentityCoreMode;
import java.util.Objects;
import java.util.Optional;

/** Applies the global core mode while keeping non-allowlisted organizations on the legacy path. */
final class ConfiguredIdentityCoreActivation implements IdentityCoreActivation {

    private final IdentityCoreProperties core;
    private final EnterpriseIdentityRolloutProperties rollout;

    ConfiguredIdentityCoreActivation(
            IdentityCoreProperties core,
            EnterpriseIdentityRolloutProperties rollout
    ) {
        this.core = Objects.requireNonNull(core, "core properties must not be null");
        this.rollout = Objects.requireNonNull(rollout, "rollout properties must not be null");
    }

    @Override
    public IdentityCoreMode modeFor(Optional<String> organizationId) {
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        if (organizationId.isPresent()
                && !rollout.getOrganizationAllowlist().contains(organizationId.orElseThrow())) {
            return IdentityCoreMode.LEGACY;
        }
        return core.getMode();
    }
}
