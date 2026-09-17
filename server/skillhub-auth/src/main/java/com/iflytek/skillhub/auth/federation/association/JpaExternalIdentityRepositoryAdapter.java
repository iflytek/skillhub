package com.iflytek.skillhub.auth.federation.association;

import com.iflytek.skillhub.auth.federation.core.ExternalIdentityCoordinate;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** JPA adapter that verifies the tenant scope after resolving the globally unique connection. */
@Repository
public class JpaExternalIdentityRepositoryAdapter implements ExternalIdentityRepository {

    private final ExternalIdentitySpringDataRepository delegate;

    public JpaExternalIdentityRepositoryAdapter(ExternalIdentitySpringDataRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<ExternalIdentity> findByCoordinate(ExternalIdentityCoordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate must not be null");
        return delegate.findByConnectionIdAndIssuerAndSubjectTypeAndSubjectValue(
                        coordinate.connectionId(),
                        coordinate.issuer().toString(),
                        coordinate.subject().type().value(),
                        coordinate.subject().value()
                )
                .filter(identity -> Objects.equals(
                        identity.getOrganizationId(),
                        coordinate.organizationId().orElse(null)
                ));
    }

    @Override
    public Optional<ExternalIdentity> findByOrganizationIdAndId(
            String organizationId,
            String id
    ) {
        return delegate.findByOrganizationIdAndId(
                requireText(organizationId, "organizationId"),
                requireText(id, "id")
        );
    }

    @Override
    public ExternalIdentity saveAndFlush(ExternalIdentity identity) {
        return delegate.saveAndFlush(Objects.requireNonNull(identity, "identity must not be null"));
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
