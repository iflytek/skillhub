package com.iflytek.skillhub.auth.federation.core;

/** Stable internal reason codes for login-policy denial and audit events. */
public enum IdentityLoginRejectionReason {
    ACCOUNT_PENDING,
    ACCOUNT_DISABLED,
    ACCOUNT_MERGED,
    SYSTEM_ACCOUNT_FORBIDDEN,
    ORGANIZATION_SUSPENDED,
    ORGANIZATION_DECOMMISSIONED,
    MEMBERSHIP_NOT_ACTIVE,
    MEMBERSHIP_SUSPENDED,
    MEMBERSHIP_DEPROVISIONED,
    CONNECTION_NOT_ACTIVE,
    EXTERNAL_IDENTITY_SUSPENDED,
    EXTERNAL_IDENTITY_REVOKED
}
