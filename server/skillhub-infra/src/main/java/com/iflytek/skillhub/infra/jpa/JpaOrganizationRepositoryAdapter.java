package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.organization.Organization;
import com.iflytek.skillhub.domain.organization.OrganizationRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/** JPA adapter for the organization aggregate repository port. */
@Repository
public class JpaOrganizationRepositoryAdapter implements OrganizationRepository {

    private static final String SLUG_CONSTRAINT = "uk_organization_slug";

    private final OrganizationSpringDataRepository delegate;

    public JpaOrganizationRepositoryAdapter(OrganizationSpringDataRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<Organization> findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public Optional<Organization> findByIdForUpdate(String id) {
        return delegate.findByIdForUpdate(id);
    }

    @Override
    public Optional<Organization> findBySlug(String slug) {
        return delegate.findBySlug(slug);
    }

    @Override
    public Organization save(Organization organization) {
        try {
            return delegate.saveAndFlush(organization);
        } catch (DataIntegrityViolationException exception) {
            if (SLUG_CONSTRAINT.equalsIgnoreCase(constraintName(exception))) {
                throw new DomainConflictException("error.organization.slug.conflict");
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
