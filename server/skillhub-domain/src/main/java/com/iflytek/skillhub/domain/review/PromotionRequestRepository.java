package com.iflytek.skillhub.domain.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

public interface PromotionRequestRepository {
    PromotionRequest save(PromotionRequest request);
    Optional<PromotionRequest> findById(Long id);
    Optional<PromotionRequest> findBySourceVersionIdAndStatus(Long sourceVersionId, ReviewTaskStatus status);
    Page<PromotionRequest> findByStatus(ReviewTaskStatus status, Pageable pageable);
    int updateStatusWithVersion(Long id, ReviewTaskStatus status, Long reviewedBy,
                               String reviewComment, Long targetSkillId, Integer expectedVersion);
}
