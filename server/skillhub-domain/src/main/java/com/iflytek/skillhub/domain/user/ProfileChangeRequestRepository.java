package com.iflytek.skillhub.domain.user;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link ProfileChangeRequest} entities.
 * Implementations are provided by the infra layer (JPA).
 */
public interface ProfileChangeRequestRepository {

    ProfileChangeRequest save(ProfileChangeRequest request);

    Optional<ProfileChangeRequest> findById(Long id);

    /**
     * Find all requests for a given user with a specific status.
     * Primarily used to locate PENDING requests when a user submits
     * a new change (so the old PENDING ones can be cancelled).
     */
    List<ProfileChangeRequest> findByUserIdAndStatus(String userId, ProfileChangeStatus status);
}
