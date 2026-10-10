package com.iflytek.skillhub.domain.organization;

/** Administrative actions evaluated only against Organization-scoped roles. */
public enum OrganizationAdministrativeAction {
    VIEW_ORGANIZATION,
    VIEW_ORGANIZATION_ROLES,
    VIEW_DOMAINS,
    VIEW_MEMBERS,
    VIEW_LOGIN_CONNECTIONS,
    MANAGE_ORGANIZATION_LIFECYCLE,
    MANAGE_ORGANIZATION_ROLES,
    MANAGE_DOMAINS,
    MANAGE_LOGIN_CONNECTIONS,
    ROTATE_LOGIN_SECRETS,
    RESOLVE_IDENTITY_CONFLICTS,
    MANAGE_MEMBERS,
    VIEW_AUDIT,
    VIEW_HEALTH
}
