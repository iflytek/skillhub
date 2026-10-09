package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.review.PromotionRevocationRequest;
import com.iflytek.skillhub.domain.review.PromotionRevocationRequestRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public interface PromotionRevocationRequestJpaRepository
        extends JpaRepository<PromotionRevocationRequest, Long>, PromotionRevocationRequestRepository {
    boolean existsByTargetSkillIdAndStatus(Long targetSkillId, ReviewTaskStatus status);
    Optional<PromotionRevocationRequest> findByTargetSkillIdAndStatus(Long targetSkillId, ReviewTaskStatus status);
    List<PromotionRevocationRequest> findBySourceSkillIdOrderBySubmittedAtDesc(Long sourceSkillId);
    List<PromotionRevocationRequest> findByStatusOrderBySubmittedAtAsc(ReviewTaskStatus status);
    Page<PromotionRevocationRequest> findByStatusIn(List<ReviewTaskStatus> statuses, Pageable pageable);
}
