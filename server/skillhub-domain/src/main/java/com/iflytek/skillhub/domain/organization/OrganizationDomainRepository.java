package com.iflytek.skillhub.domain.organization;

import java.util.Optional;

/** Tenant-scoped persistence port with one explicit verified-ownership lookup. */
public interface OrganizationDomainRepository {

    Optional<OrganizationDomain> findByOrganizationIdAndId(
            String organizationId,
            String domainId
    );

    Optional<OrganizationDomain> findByOrganizationIdAndDomain(
            String organizationId,
            String domain
    );

    Optional<OrganizationDomain> findVerifiedByDomain(String domain);

    /** Serializes identity association with verified-domain disable or transfer operations. */
    Optional<OrganizationDomain> findVerifiedByDomainForUpdate(String domain);

    OrganizationDomain save(OrganizationDomain domain);
}
