package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.organization.OrganizationDomain;
import com.iflytek.skillhub.domain.organization.OrganizationDomainStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Internal Spring Data delegate for organization-domain persistence. */
interface OrganizationDomainSpringDataRepository
        extends JpaRepository<OrganizationDomain, String> {

    Optional<OrganizationDomain> findByOrganizationIdAndId(
            String organizationId,
            String domainId
    );

    Optional<OrganizationDomain> findByOrganizationIdAndDomain(
            String organizationId,
            String domain
    );

    Optional<OrganizationDomain> findByDomainAndStatus(
            String domain,
            OrganizationDomainStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select domain
            from OrganizationDomain domain
            where domain.domain = :domain and domain.status = :status
            """)
    Optional<OrganizationDomain> findVerifiedOwnershipForUpdate(
            @Param("domain") String domain,
            @Param("status") OrganizationDomainStatus status
    );
}
