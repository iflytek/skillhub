package com.iflytek.skillhub.auth.federation.association;

import com.iflytek.skillhub.auth.federation.core.ExternalIdentityCoordinate;
import java.util.List;
import java.util.Optional;

/** Persistence port for trusted first-login subject claims. */
public interface PreProvisionedLoginSubjectRepository {

    Optional<PreProvisionedLoginSubject> findByCoordinate(
            ExternalIdentityCoordinate coordinate
    );

    List<PreProvisionedLoginSubject> findActiveByMembership(
            String organizationId,
            String membershipId
    );

    PreProvisionedLoginSubject saveAndFlush(PreProvisionedLoginSubject subject);
}
