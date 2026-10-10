package com.iflytek.skillhub.auth.policy;

/**
 * Possible outcomes when evaluating whether an externally authenticated user may access the
 * platform.
 */
public enum AccessDecision {
    ALLOW, DENY, PENDING_APPROVAL
}
