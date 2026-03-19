package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.review.PromotionRequest;
import com.iflytek.skillhub.domain.review.PromotionRequestRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * JPA-backed repository for promotion requests, including optimistic status updates.
 */
@Repository
public interface PromotionRequestJpaRepository extends JpaRepository<PromotionRequest, Long>,
                                                       PromotionRequestRepository {

    Optional<PromotionRequest> findBySourceVersionIdAndStatus(Long sourceVersionId, ReviewTaskStatus status);

    Optional<PromotionRequest> findBySourceSkillIdAndStatus(Long sourceSkillId, ReviewTaskStatus status);

    Page<PromotionRequest> findByStatus(ReviewTaskStatus status, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE PromotionRequest p
        SET p.status = :status,
            p.reviewedBy = :reviewedBy,
            p.reviewComment = :reviewComment,
            p.targetSkillId = :targetSkillId,
            p.reviewedAt = CURRENT_TIMESTAMP,
            p.version = p.version + 1
        WHERE p.id = :id AND p.version = :expectedVersion
    """)
    int updateStatusWithVersion(@Param("id") Long id,
                               @Param("status") ReviewTaskStatus status,
                               @Param("reviewedBy") String reviewedBy,
                               @Param("reviewComment") String reviewComment,
                               @Param("targetSkillId") Long targetSkillId,
                               @Param("expectedVersion") Integer expectedVersion);
}
