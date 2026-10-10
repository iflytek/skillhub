package com.iflytek.skillhub.domain.organization;

/** Least-privilege administrative roles scoped to one Organization. */
public enum OrganizationRole {
    ORG_OWNER,
    IDENTITY_ADMIN,
    LOGIN_SECRET_ADMIN,
    MEMBER_ADMIN,
    ORG_AUDITOR
}
