package com.iflytek.skillhub.auth.federation.core;

/** Enterprise membership states relevant to the final login guard. */
public enum MembershipLoginState {
    INVITED,
    PROVISIONED,
    ACTIVE,
    SUSPENDED,
    DEPROVISIONED
}
