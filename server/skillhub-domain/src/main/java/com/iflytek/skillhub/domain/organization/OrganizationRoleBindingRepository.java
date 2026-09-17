package com.iflytek.skillhub.domain.organization;

import java.util.List;
import java.util.Optional;

/** Tenant-scoped repository port for Organization administrative role bindings. */
public interface OrganizationRoleBindingRepository {

    Optional<OrganizationRoleBinding> findByOrganizationIdAndId(
            String organizationId,
            String bindingId
    );

    List<OrganizationRoleBinding> findActiveByOrganizationIdAndUserId(
            String organizationId,
            String userId
    );

    Optional<OrganizationRoleBinding> findActiveByOrganizationIdAndUserIdAndRole(
            String organizationId,
            String userId,
            OrganizationRole role
    );

    long countActiveByOrganizationIdAndRole(
            String organizationId,
            OrganizationRole role
    );

    OrganizationRoleBinding save(OrganizationRoleBinding binding);
}
