package com.iflytek.skillhub.auth.federation.association;

import com.iflytek.skillhub.auth.federation.core.ExternalIdentityCoordinate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** JPA adapter that refuses platform-scoped coordinates before querying enterprise claims. */
@Repository
public class JpaPreProvisionedLoginSubjectRepositoryAdapter
        implements PreProvisionedLoginSubjectRepository {

    private final PreProvisionedLoginSubjectSpringDataRepository delegate;

    public JpaPreProvisionedLoginSubjectRepositoryAdapter(
            PreProvisionedLoginSubjectSpringDataRepository delegate
    ) {
        this.delegate = delegate;
    }

    @Override
    public List<PreProvisionedLoginSubject> findActiveByMembership(
            String organizationId,
            String membershipId
    ) {
        return delegate.findByOrganizationIdAndMembershipIdAndStatus(
                Objects.requireNonNull(organizationId, "organizationId"),
                Objects.requireNonNull(membershipId, "membershipId"),
                PreProvisionedLoginSubjectStatus.ACTIVE
        );
    }

    @Override
    public Optional<PreProvisionedLoginSubject> findByCoordinate(
            ExternalIdentityCoordinate coordinate
    ) {
        Objects.requireNonNull(coordinate, "coordinate must not be null");
        return coordinate.organizationId().flatMap(organizationId ->
                delegate
                        .findByOrganizationIdAndLoginConnectionIdAndIssuerAndSubjectTypeAndSubjectValue(
                                organizationId,
                                coordinate.connectionId(),
                                coordinate.issuer().toString(),
                                coordinate.subject().type().value(),
                                coordinate.subject().value()
                        )
        );
    }

    @Override
    public PreProvisionedLoginSubject saveAndFlush(PreProvisionedLoginSubject subject) {
        return delegate.saveAndFlush(Objects.requireNonNull(subject, "subject must not be null"));
    }
}
