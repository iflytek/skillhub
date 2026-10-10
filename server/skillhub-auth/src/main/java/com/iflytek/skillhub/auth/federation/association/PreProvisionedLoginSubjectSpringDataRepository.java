package com.iflytek.skillhub.auth.federation.association;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal Spring Data delegate for exact, tenant-scoped subject claims. */
interface PreProvisionedLoginSubjectSpringDataRepository
        extends JpaRepository<PreProvisionedLoginSubject, String> {

    Optional<PreProvisionedLoginSubject>
            findByOrganizationIdAndLoginConnectionIdAndIssuerAndSubjectTypeAndSubjectValue(
                    String organizationId,
                    String loginConnectionId,
                    String issuer,
                    String subjectType,
                    String subjectValue
            );

    List<PreProvisionedLoginSubject> findByOrganizationIdAndMembershipIdAndStatus(
            String organizationId,
            String membershipId,
            PreProvisionedLoginSubjectStatus status
    );
}
