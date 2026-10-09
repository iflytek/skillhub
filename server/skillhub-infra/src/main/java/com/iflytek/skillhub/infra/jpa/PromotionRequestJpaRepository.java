package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.review.PromotionRequest;
import com.iflytek.skillhub.domain.review.PromotionRequestRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * JPA-backed repository for promotion requests, including optimistic status updates.
 */
@Repository
public interface PromotionRequestJpaRepository extends JpaRepository<PromotionRequest, Long>,
                                                       PromotionRequestRepository {

    Optional<PromotionRequest> findBySourceVersionIdAndStatus(Long sourceVersionId, ReviewTaskStatus status);

    Optional<PromotionRequest> findBySourceSkillIdAndStatus(Long sourceSkillId, ReviewTaskStatus status);

    @Query("SELECT p FROM PromotionRequest p WHERE p.sourceSkillId = :sourceSkillId AND p.requestKind = com.iflytek.skillhub.domain.review.PromotionRequestKind.INITIAL AND p.status = com.iflytek.skillhub.domain.review.ReviewTaskStatus.APPROVED AND p.targetSkillId IS NOT NULL")
    Optional<PromotionRequest> findActiveInitialBySourceSkillId(@Param("sourceSkillId") Long sourceSkillId);

    Page<PromotionRequest> findByStatus(ReviewTaskStatus status, Pageable pageable);

    @Query(
            value = """
                SELECT p
                FROM PromotionRequest p
                WHERE p.status = :status
                ORDER BY CASE WHEN p.reviewedAt IS NULL THEN 1 ELSE 0 END ASC,
                         p.reviewedAt ASC,
                         p.id ASC
            """,
            countQuery = "SELECT COUNT(p) FROM PromotionRequest p WHERE p.status = :status"
    )
    Page<PromotionRequest> findHistoryByStatusOrderByReviewedAtAsc(@Param("status") ReviewTaskStatus status,
                                                                   Pageable pageable);

    @Query(
            value = """
                SELECT p
                FROM PromotionRequest p
                WHERE p.status = :status
                ORDER BY CASE WHEN p.reviewedAt IS NULL THEN 1 ELSE 0 END ASC,
                         p.reviewedAt DESC,
                         p.id DESC
            """,
            countQuery = "SELECT COUNT(p) FROM PromotionRequest p WHERE p.status = :status"
    )
    Page<PromotionRequest> findHistoryByStatusOrderByReviewedAtDesc(@Param("status") ReviewTaskStatus status,
                                                                    Pageable pageable);

    boolean existsByTargetNamespaceId(Long targetNamespaceId);

    void deleteBySourceSkillIdOrTargetSkillId(Long sourceSkillId, Long targetSkillId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE PromotionRequest p
        SET p.status = :status,
            p.reviewedBy = :reviewedBy,
            p.reviewComment = :reviewComment,
            p.targetSkillId = COALESCE(:targetSkillId, p.targetSkillId),
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
