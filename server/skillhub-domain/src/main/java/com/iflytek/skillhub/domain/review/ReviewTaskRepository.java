package com.iflytek.skillhub.domain.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

/**
 * Domain repository contract for moderation review tasks and their state transitions.
 */
public interface ReviewTaskRepository {
    ReviewTask save(ReviewTask reviewTask);
    Optional<ReviewTask> findById(Long id);
    Optional<ReviewTask> findBySkillVersionIdAndStatus(Long skillVersionId, ReviewTaskStatus status);
    Page<ReviewTask> findByStatus(ReviewTaskStatus status, Pageable pageable);
    Page<ReviewTask> findByNamespaceIdAndStatus(Long namespaceId, ReviewTaskStatus status, Pageable pageable);
    Page<ReviewTask> findBySubmittedByAndStatus(String submittedBy, ReviewTaskStatus status, Pageable pageable);
    void delete(ReviewTask reviewTask);
    int updateStatusWithVersion(Long id, ReviewTaskStatus status, String reviewedBy,
                               String reviewComment, Integer expectedVersion);
}
