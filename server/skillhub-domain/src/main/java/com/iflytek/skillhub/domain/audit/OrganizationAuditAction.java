package com.iflytek.skillhub.domain.audit;

/** Stable action taxonomy for Organization administration audit records. */
public enum OrganizationAuditAction {
    ORGANIZATION_CREATED,
    ORGANIZATION_SUSPENDED,
    ORGANIZATION_REACTIVATED,
    ORGANIZATION_DECOMMISSIONED,
    DOMAIN_CHALLENGE_ISSUED,
    DOMAIN_VERIFIED,
    DOMAIN_DISABLED,
    MEMBER_ADDED,
    MEMBER_SUSPENDED,
    MEMBER_REACTIVATED,
    MEMBER_DEPROVISIONED,
    ROLE_GRANTED,
    ROLE_REVOKED
}
