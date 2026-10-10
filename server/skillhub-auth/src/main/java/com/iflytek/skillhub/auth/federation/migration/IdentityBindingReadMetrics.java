package com.iflytek.skillhub.auth.federation.migration;

import com.iflytek.skillhub.auth.federation.core.IdentityCoreMode;

/** Observability port for binding migration reads; implementations must use bounded tags only. */
@FunctionalInterface
public interface IdentityBindingReadMetrics {

    void record(IdentityCoreMode mode, IdentityBindingReadOutcome outcome);

    static IdentityBindingReadMetrics noop() {
        return (mode, outcome) -> { };
    }
}
