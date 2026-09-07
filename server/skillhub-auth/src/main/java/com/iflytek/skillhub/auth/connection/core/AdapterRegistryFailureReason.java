package com.iflytek.skillhub.auth.connection.core;

/** Stable failure categories for adapter registration and lookup. */
public enum AdapterRegistryFailureReason {
    DUPLICATE_REGISTRATION,
    UNSUPPORTED_CONTRACT_VERSION,
    INVALID_INTERACTION_MODEL,
    INVALID_CAPABILITY_SET,
    MISSING_REQUIRED_CAPABILITY,
    IMPLEMENTATION_MISMATCH,
    ADAPTER_NOT_REGISTERED
}
