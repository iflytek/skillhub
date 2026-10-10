package com.iflytek.skillhub.auth.federation.migration;

/** Low-cardinality outcomes emitted by the legacy-to-V2 identity binding read seam. */
public enum IdentityBindingReadOutcome {
    LEGACY_HIT,
    LEGACY_MISS,
    MATCH,
    V2_MISS_LEGACY_FALLBACK,
    V2_ONLY,
    MISMATCH,
    V2_UNAVAILABLE
}
