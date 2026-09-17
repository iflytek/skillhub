package com.iflytek.skillhub.auth.federation.core;

import java.util.Optional;

/** Resolves the rollout mode for a platform or organization-scoped identity assertion. */
@FunctionalInterface
public interface IdentityCoreActivation {

    IdentityCoreMode modeFor(Optional<String> organizationId);
}
