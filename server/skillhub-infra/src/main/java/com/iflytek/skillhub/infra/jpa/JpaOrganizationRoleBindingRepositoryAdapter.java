package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.organization.OrganizationRole;
import com.iflytek.skillhub.domain.organization.OrganizationRoleBinding;
import com.iflytek.skillhub.domain.organization.OrganizationRoleBindingRepository;
import com.iflytek.skillhub.domain.organization.OrganizationRoleBindingStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** JPA adapter exposing only tenant-scoped Organization role-binding operations. */
@Repository
public class JpaOrganizationRoleBindingRepositoryAdapter
        implements OrganizationRoleBindingRepository {

    private final OrganizationRoleBindingSpringDataRepository delegate;

    public JpaOrganizationRoleBindingRepositoryAdapter(
            OrganizationRoleBindingSpringDataRepository delegate
    ) {
        this.delegate = delegate;
    }

    @Override
    public Optional<OrganizationRoleBinding> findByOrganizationIdAndId(
            String organizationId,
            String bindingId
    ) {
        return delegate.findByOrganizationIdAndId(organizationId, bindingId);
    }

    @Override
    public List<OrganizationRoleBinding> findActiveByOrganizationIdAndUserId(
            String organizationId,
            String userId
    ) {
        return delegate.findByOrganizationIdAndUserIdAndStatus(
                organizationId,
                userId,
                OrganizationRoleBindingStatus.ACTIVE
        );
    }

    @Override
    public Optional<OrganizationRoleBinding> findActiveByOrganizationIdAndUserIdAndRole(
            String organizationId,
            String userId,
            OrganizationRole role
    ) {
        return delegate.findByOrganizationIdAndUserIdAndRoleAndStatus(
                organizationId,
                userId,
                role,
                OrganizationRoleBindingStatus.ACTIVE
        );
    }

    @Override
    public long countActiveByOrganizationIdAndRole(
            String organizationId,
            OrganizationRole role
    ) {
        return delegate.countByOrganizationIdAndRoleAndStatus(
                organizationId,
                role,
                OrganizationRoleBindingStatus.ACTIVE
        );
    }

    @Override
    public OrganizationRoleBinding save(OrganizationRoleBinding binding) {
        return delegate.save(binding);
    }
}
