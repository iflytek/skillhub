package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.organization.Organization;
import com.iflytek.skillhub.domain.organization.OrganizationDomain;
import com.iflytek.skillhub.domain.organization.OrganizationRole;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Management read models for the Organization foundation, always explicitly tenant scoped. */
public interface EnterpriseIdentityQueryRepository {

    Page<Organization> findOrganizations(Pageable pageable);

    Page<Organization> findActiveOrganizationsByUserId(String userId, Pageable pageable);

    Map<String, Set<OrganizationRole>> findActiveRoles(
            List<String> organizationIds,
            String userId
    );

    Page<OrganizationDomain> findDomains(String organizationId, Pageable pageable);

    Page<OrganizationMembershipView> findMemberships(
            String organizationId,
            Pageable pageable
    );

    Page<OrganizationRoleBindingView> findRoleBindings(
            String organizationId,
            Pageable pageable
    );
}
