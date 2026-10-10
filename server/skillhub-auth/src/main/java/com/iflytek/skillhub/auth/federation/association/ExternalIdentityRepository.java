package com.iflytek.skillhub.auth.federation.association;

import com.iflytek.skillhub.auth.federation.core.ExternalIdentityCoordinate;
import java.util.Optional;

/** Persistence port for exact external identity bindings. */
public interface ExternalIdentityRepository {

    Optional<ExternalIdentity> findByCoordinate(ExternalIdentityCoordinate coordinate);

    Optional<ExternalIdentity> findByOrganizationIdAndId(String organizationId, String id);

    ExternalIdentity saveAndFlush(ExternalIdentity identity);
}
