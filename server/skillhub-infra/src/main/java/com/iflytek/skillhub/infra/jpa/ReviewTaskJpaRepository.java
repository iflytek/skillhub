package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewDecisionMethod;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.review.ReviewSubjectType;
import com.iflytek.skillhub.domain.review.ReviewOriginType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed repository for review tasks, including optimistic update support for moderation
 * decisions.
 */
@Repository
public interface ReviewTaskJpaRepository extends JpaRepository<ReviewTask, Long>, ReviewTaskRepository {

    List<ReviewTask> findByAvatarCandidateIdAndStatus(Long candidateId, ReviewTaskStatus status);

    Optional<ReviewTask> findBySkillVersionIdAndStatus(Long skillVersionId, ReviewTaskStatus status);

    List<ReviewTask> findBySkillIdAndStatus(Long skillId, ReviewTaskStatus status);

    @Override
    Optional<ReviewTask> findBySubjectTypeAndSubjectVersionIdAndStatus(
            ReviewSubjectType subjectType, Long subjectVersionId, ReviewTaskStatus status);

    @Override
    default Page<ReviewTask> findByStatus(ReviewTaskStatus status, Pageable pageable) {
        return findByOriginTypeAndStatus(ReviewOriginType.USER, status, pageable);
    }

    @Override
    default Page<ReviewTask> findByNamespaceIdAndStatus(Long namespaceId,
                                                         ReviewTaskStatus status, Pageable pageable) {
        return findByNamespaceIdAndOriginTypeAndStatus(namespaceId, ReviewOriginType.USER, status, pageable);
    }

    @Override
    Page<ReviewTask> findByOriginTypeAndStatus(ReviewOriginType originType, ReviewTaskStatus status,
                                               Pageable pageable);

    @Override
    Page<ReviewTask> findByNamespaceIdAndOriginTypeAndStatus(Long namespaceId,
                                                              ReviewOriginType originType,
                                                              ReviewTaskStatus status, Pageable pageable);

    Page<ReviewTask> findBySubmittedByAndStatus(String submittedBy, ReviewTaskStatus status, Pageable pageable);

    @Override
    List<ReviewTask> findByNamespaceIdAndSubmittedByAndStatus(
            Long namespaceId, String submittedBy, ReviewTaskStatus status);

    @Override
    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE ReviewTask t
        SET t.autoPublicationCancelled = true
        WHERE t.id = :id
          AND t.status = com.iflytek.skillhub.domain.review.ReviewTaskStatus.PENDING
    """)
    int cancelPendingAutoPublication(@Param("id") Long id);

    @Override
    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE ReviewTask t
        SET t.manualReviewNotified = true
        WHERE t.id = :id
          AND t.manualReviewNotified = false
          AND t.status = com.iflytek.skillhub.domain.review.ReviewTaskStatus.PENDING
    """)
    int markPendingManualReviewNotified(@Param("id") Long id);

    @Override
    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE ReviewTask t
        SET t.decisionMethod = :method,
            t.decisionScanTaskId = :scanTaskId
        WHERE t.id = :id
    """)
    int recordDecision(@Param("id") Long id,
                       @Param("method") ReviewDecisionMethod method,
                       @Param("scanTaskId") String scanTaskId);

    List<ReviewTask> findBySubmittedByAndSkillIdAndSkillVersionOrderBySubmittedAtDescIdDesc(
            String submittedBy, Long skillId, String skillVersion);

    List<ReviewTask> findBySkillIdAndSkillVersionOrderBySubmittedAtDescIdDesc(
            Long skillId, String skillVersion);

    List<ReviewTask> findBySubmittedByAndSubjectTypeAndSubjectIdAndSubjectVersionOrderBySubmittedAtDescIdDesc(
            String submittedBy,
            ReviewSubjectType subjectType,
            Long subjectId,
            String subjectVersion);

    List<ReviewTask> findBySubjectTypeAndSubjectIdAndSubjectVersionOrderBySubmittedAtDescIdDesc(
            ReviewSubjectType subjectType,
            Long subjectId,
            String subjectVersion);

    boolean existsByNamespaceId(Long namespaceId);

    void deleteBySkillVersionIdIn(Collection<Long> skillVersionIds);

    void deleteBySkillId(Long skillId);

    @Override
    void deleteBySubjectTypeAndSubjectId(ReviewSubjectType subjectType, Long subjectId);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE ReviewTask t
        SET t.status = :status,
            t.reviewedBy = :reviewedBy,
            t.reviewComment = :reviewComment,
            t.reviewedAt = CURRENT_TIMESTAMP,
            t.version = t.version + 1
        WHERE t.id = :id AND t.version = :expectedVersion
    """)
    int updateStatusWithVersion(@Param("id") Long id,
                               @Param("status") ReviewTaskStatus status,
                               @Param("reviewedBy") String reviewedBy,
                               @Param("reviewComment") String reviewComment,
                               @Param("expectedVersion") Integer expectedVersion);
}
