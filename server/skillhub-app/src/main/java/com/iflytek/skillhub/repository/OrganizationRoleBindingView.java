package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.organization.OrganizationRoleBinding;

/** Read projection joining a tenant-scoped role binding to its Platform Account. */
public record OrganizationRoleBindingView(
        OrganizationRoleBinding binding,
        String accountDisplayName,
        String accountEmail
) {
}
