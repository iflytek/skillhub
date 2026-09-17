package com.iflytek.skillhub.auth.federation.core;

/** Fixed low-cardinality security events emitted by the enterprise identity boundary. */
public enum EnterpriseIdentitySecurityEventType {
    IDENTITY_CORRELATION_CONFLICT,
    MISSING_ACCOUNT_LOGIN_REJECTED,
    INACTIVE_ACCOUNT_LOGIN_REJECTED,
    MERGED_ACCOUNT_LOGIN_REJECTED,
    SYSTEM_ACCOUNT_LOGIN_REJECTED,
    ENTERPRISE_SESSION_REVOKED
}
