package com.iflytek.skillhub.domain.review;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PromotionRevocationRequestRepository {
    PromotionRevocationRequest save(PromotionRevocationRequest request);
    Optional<PromotionRevocationRequest> findById(Long id);
    boolean existsByTargetSkillIdAndStatus(Long targetSkillId, ReviewTaskStatus status);
    Optional<PromotionRevocationRequest> findByTargetSkillIdAndStatus(Long targetSkillId, ReviewTaskStatus status);
    List<PromotionRevocationRequest> findBySourceSkillIdOrderBySubmittedAtDesc(Long sourceSkillId);
    List<PromotionRevocationRequest> findByStatusOrderBySubmittedAtAsc(ReviewTaskStatus status);
    Page<PromotionRevocationRequest> findByStatusIn(List<ReviewTaskStatus> statuses, Pageable pageable);
}
