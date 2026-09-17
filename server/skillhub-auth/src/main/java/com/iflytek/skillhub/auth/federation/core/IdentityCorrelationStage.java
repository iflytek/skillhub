package com.iflytek.skillhub.auth.federation.core;

/** Ordered correlation stage that produced a match or conflict. */
public enum IdentityCorrelationStage {
    EXACT_BINDING,
    PRE_PROVISIONED_SUBJECT,
    VERIFIED_EMAIL,
    JIT_PROVISIONING
}
