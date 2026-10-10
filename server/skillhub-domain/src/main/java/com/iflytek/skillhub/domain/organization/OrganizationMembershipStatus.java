package com.iflytek.skillhub.domain.organization;

/** Lifecycle state of a person inside one enterprise tenant. */
public enum OrganizationMembershipStatus {
    INVITED,
    PROVISIONED,
    ACTIVE,
    SUSPENDED,
    DEPROVISIONED
}
