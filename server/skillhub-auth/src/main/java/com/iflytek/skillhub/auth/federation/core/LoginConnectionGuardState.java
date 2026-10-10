package com.iflytek.skillhub.auth.federation.core;

/** Login-connection states relevant to the final login guard. */
public enum LoginConnectionGuardState {
    DRAFT,
    ACTIVE,
    SUSPENDED,
    ERROR,
    DISABLED
}
