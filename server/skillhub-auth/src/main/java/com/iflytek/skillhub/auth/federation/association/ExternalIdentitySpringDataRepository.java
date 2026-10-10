package com.iflytek.skillhub.auth.federation.association;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal Spring Data delegate; callers use the protocol-neutral repository port. */
interface ExternalIdentitySpringDataRepository
        extends JpaRepository<ExternalIdentity, String> {

    Optional<ExternalIdentity> findByOrganizationIdAndId(String organizationId, String id);

    Optional<ExternalIdentity> findByConnectionIdAndIssuerAndSubjectTypeAndSubjectValue(
            String connectionId,
            String issuer,
            String subjectType,
            String subjectValue
    );
}
