package com.iflytek.skillhub.domain.bundle;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

/**
 * Persistence contract for {@link SkillBundleReviewTask}.
 */
public interface SkillBundleReviewTaskRepository {
    SkillBundleReviewTask save(SkillBundleReviewTask task);
    Optional<SkillBundleReviewTask> findById(Long id);
    Optional<SkillBundleReviewTask> findByBundleVersionId(Long bundleVersionId);
    Page<SkillBundleReviewTask> findByStatusAndNamespaceId(String status, Long namespaceId, Pageable pageable);

    /**
     * Optimistically transitions the task into the new status.
     * @return the number of rows updated; 0 means another writer beat us to it.
     */
    int updateStatusWithVersion(Long id, String newStatus, String reviewedBy,
                                String reviewComment, Integer expectedVersion);
}
