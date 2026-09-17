package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.organization.OrganizationDomain;
import com.iflytek.skillhub.domain.organization.OrganizationDomainRepository;
import com.iflytek.skillhub.domain.organization.OrganizationDomainStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/** JPA adapter with tenant-scoped reads and stable domain ownership conflicts. */
@Repository
public class JpaOrganizationDomainRepositoryAdapter
        implements OrganizationDomainRepository {

    private static final String CLAIM_CONSTRAINT = "uk_organization_domain_claim";
    private static final String VERIFIED_OWNER_CONSTRAINT =
            "uk_organization_domain_verified_owner";

    private final OrganizationDomainSpringDataRepository delegate;

    public JpaOrganizationDomainRepositoryAdapter(
            OrganizationDomainSpringDataRepository delegate
    ) {
        this.delegate = delegate;
    }

    @Override
    public Optional<OrganizationDomain> findByOrganizationIdAndId(
            String organizationId,
            String domainId
    ) {
        return delegate.findByOrganizationIdAndId(organizationId, domainId);
    }

    @Override
    public Optional<OrganizationDomain> findByOrganizationIdAndDomain(
            String organizationId,
            String domain
    ) {
        return delegate.findByOrganizationIdAndDomain(organizationId, domain);
    }

    @Override
    public Optional<OrganizationDomain> findVerifiedByDomain(String domain) {
        return delegate.findByDomainAndStatus(domain, OrganizationDomainStatus.VERIFIED);
    }

    @Override
    public Optional<OrganizationDomain> findVerifiedByDomainForUpdate(String domain) {
        return delegate.findVerifiedOwnershipForUpdate(
                domain,
                OrganizationDomainStatus.VERIFIED
        );
    }

    @Override
    public OrganizationDomain save(OrganizationDomain domain) {
        try {
            return delegate.saveAndFlush(domain);
        } catch (DataIntegrityViolationException exception) {
            String constraintName = constraintName(exception);
            if (CLAIM_CONSTRAINT.equalsIgnoreCase(constraintName)) {
                throw new DomainConflictException(
                        "error.organization.domain.claim-conflict"
                );
            }
            if (VERIFIED_OWNER_CONSTRAINT.equalsIgnoreCase(constraintName)) {
                throw new DomainConflictException(
                        "error.organization.domain.ownership-conflict"
                );
            }
            throw exception;
        }
    }

    private String constraintName(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
            current = current.getCause();
        }
        return null;
    }
}
