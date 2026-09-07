package com.iflytek.skillhub.auth.connection.core;

/** Stable reasons why a revision cannot become the active runtime snapshot. */
public enum ConnectionRevisionIncompatibility {
    CONNECTION_IDENTITY_MISMATCH,
    NON_MONOTONIC_ACTIVATION,
    INVALID_ROLLBACK_TARGET,
    CONTRACT_MINOR_UNSUPPORTED,
    CONFIG_SCHEMA_UNSUPPORTED,
    CAPABILITY_UNSUPPORTED,
    DESCRIPTOR_SHAPE_UNSUPPORTED
}
