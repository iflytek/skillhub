package com.iflytek.skillhub.auth.federation.association;

/** Stable, privacy-preserving failure classification for first-login association. */
public enum EnterpriseIdentityAssociationFailure {
    ORGANIZATION_REQUIRED,
    NO_SAFE_MATCH,
    CORRELATION_CONFLICT,
    BINDING_UNAVAILABLE,
    MEMBERSHIP_UNAVAILABLE,
    ACCOUNT_UNAVAILABLE,
    ACCOUNT_REAUTHENTICATION_REQUIRED,
    CONCURRENT_ASSOCIATION_FAILED
}
