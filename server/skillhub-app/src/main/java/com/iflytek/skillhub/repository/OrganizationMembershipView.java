package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.organization.OrganizationMembership;

/** Read projection joining a tenant-scoped membership to its optional Platform Account. */
public record OrganizationMembershipView(
        OrganizationMembership membership,
        String accountDisplayName,
        String accountEmail
) {
}
