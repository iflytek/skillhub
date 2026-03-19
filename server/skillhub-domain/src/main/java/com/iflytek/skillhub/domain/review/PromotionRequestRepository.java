package com.iflytek.skillhub.domain.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

/**
 * Domain repository contract for promotion requests that copy or elevate one skill version into a
 * target catalog entry.
 */
public interface PromotionRequestRepository {
    PromotionRequest save(PromotionRequest request);
    Optional<PromotionRequest> findById(Long id);
    Optional<PromotionRequest> findBySourceVersionIdAndStatus(Long sourceVersionId, ReviewTaskStatus status);
    Optional<PromotionRequest> findBySourceSkillIdAndStatus(Long sourceSkillId, ReviewTaskStatus status);
    Page<PromotionRequest> findByStatus(ReviewTaskStatus status, Pageable pageable);
    int updateStatusWithVersion(Long id, ReviewTaskStatus status, String reviewedBy,
                               String reviewComment, Long targetSkillId, Integer expectedVersion);
}
