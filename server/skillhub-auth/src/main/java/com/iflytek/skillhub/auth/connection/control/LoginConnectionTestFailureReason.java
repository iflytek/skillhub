package com.iflytek.skillhub.auth.connection.control;

/** Bounded, non-sensitive categories safe for persistence, APIs, metrics and UI copy. */
public enum LoginConnectionTestFailureReason {
    CONFIGURATION,
    NETWORK,
    TLS,
    METADATA,
    CREDENTIAL,
    CLAIM_SCHEMA,
    PERMISSION,
    UPSTREAM,
    UNEXPECTED
}
