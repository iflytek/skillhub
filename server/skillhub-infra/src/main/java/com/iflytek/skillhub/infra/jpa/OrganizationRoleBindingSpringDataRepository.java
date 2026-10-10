package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.organization.OrganizationRole;
import com.iflytek.skillhub.domain.organization.OrganizationRoleBinding;
import com.iflytek.skillhub.domain.organization.OrganizationRoleBindingStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal Spring Data delegate; every role-binding query includes its Organization. */
interface OrganizationRoleBindingSpringDataRepository
        extends JpaRepository<OrganizationRoleBinding, String> {

    Optional<OrganizationRoleBinding> findByOrganizationIdAndId(
            String organizationId,
            String bindingId
    );

    List<OrganizationRoleBinding> findByOrganizationIdAndUserIdAndStatus(
            String organizationId,
            String userId,
            OrganizationRoleBindingStatus status
    );

    Optional<OrganizationRoleBinding> findByOrganizationIdAndUserIdAndRoleAndStatus(
            String organizationId,
            String userId,
            OrganizationRole role,
            OrganizationRoleBindingStatus status
    );

    long countByOrganizationIdAndRoleAndStatus(
            String organizationId,
            OrganizationRole role,
            OrganizationRoleBindingStatus status
    );
}
