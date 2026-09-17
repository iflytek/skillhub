package com.iflytek.skillhub.auth.federation.core;

/** Raised when callers attempt to enter the unified core while the legacy path is selected. */
public final class IdentityCoreDisabledException extends IllegalStateException {

    public IdentityCoreDisabledException() {
        super("Unified identity core is disabled");
    }
}
