package com.iflytek.skillhub.domain.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /** Paginated query filtered by status, ordered by created_at DESC. */
    Page<ProfileChangeRequest> findByStatusOrderByCreatedAtDesc(ProfileChangeStatus status, Pageable pageable);

    /** Find the most recent request for a user with any of the given statuses. */
    Optional<ProfileChangeRequest> findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
            String userId, Collection<ProfileChangeStatus> statuses);
}
