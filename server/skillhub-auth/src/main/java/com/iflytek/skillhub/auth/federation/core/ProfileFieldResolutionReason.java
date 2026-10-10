package com.iflytek.skillhub.auth.federation.core;

/** Stable reason codes for preserving or rejecting a proposed profile value. */
public enum ProfileFieldResolutionReason {
    SOURCE_NOT_AUTHORIZED,
    LOWER_AUTHORITY,
    EQUAL_AUTHORITY_CONFLICT
}
