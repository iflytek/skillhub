package com.iflytek.skillhub.domain.organization;

import java.util.Optional;

/** Domain repository port for organization aggregate persistence. */
public interface OrganizationRepository {

    Optional<Organization> findById(String id);

    /** Serializes identity association with Organization lifecycle changes. */
    Optional<Organization> findByIdForUpdate(String id);

    Optional<Organization> findBySlug(String slug);

    Organization save(Organization organization);
}
