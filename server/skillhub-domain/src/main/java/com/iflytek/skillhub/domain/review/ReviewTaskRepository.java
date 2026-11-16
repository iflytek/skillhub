package com.iflytek.skillhub.domain.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Domain repository contract for moderation review tasks and their state transitions.
 */
public interface ReviewTaskRepository {
    default List<ReviewTask> findByAvatarCandidateIdAndStatus(Long candidateId, ReviewTaskStatus status) { return List.of(); }
    Optional<ReviewTask> findBySkillIdAndRequestedVisibilityIsNotNullAndStatus(Long skillId, ReviewTaskStatus status);
    boolean existsBySkillIdAndRequestedVisibilityIsNotNullAndStatus(Long skillId, ReviewTaskStatus status);
    ReviewTask save(ReviewTask reviewTask);
    Optional<ReviewTask> findById(Long id);
    Optional<ReviewTask> findBySkillVersionIdAndStatus(Long skillVersionId, ReviewTaskStatus status);
    default List<ReviewTask> findBySkillIdAndStatus(Long skillId, ReviewTaskStatus status) {
        throw new UnsupportedOperationException("Skill-scoped review queries are not supported");
    }
    default Optional<ReviewTask> findBySubjectTypeAndSubjectVersionIdAndStatus(
            ReviewSubjectType subjectType, Long subjectVersionId, ReviewTaskStatus status) {
        throw new UnsupportedOperationException("Typed review subjects are not supported by this repository");
    }
    Page<ReviewTask> findByStatus(ReviewTaskStatus status, Pageable pageable);
    Page<ReviewTask> findByNamespaceIdAndStatus(Long namespaceId, ReviewTaskStatus status, Pageable pageable);
    default Page<ReviewTask> findByOriginTypeAndStatus(ReviewOriginType originType,
                                                       ReviewTaskStatus status, Pageable pageable) {
        throw new UnsupportedOperationException("Origin-specific review queries are not supported");
    }
    default Page<ReviewTask> findByNamespaceIdAndOriginTypeAndStatus(Long namespaceId,
                                                                      ReviewOriginType originType,
                                                                      ReviewTaskStatus status, Pageable pageable) {
        throw new UnsupportedOperationException("Origin-specific review queries are not supported");
    }
    Page<ReviewTask> findBySubmittedByAndStatus(String submittedBy, ReviewTaskStatus status, Pageable pageable);
    List<ReviewTask> findBySubmittedByAndSkillIdAndSkillVersionOrderBySubmittedAtDescIdDesc(
            String submittedBy, Long skillId, String skillVersion);
    List<ReviewTask> findBySkillIdAndSkillVersionOrderBySubmittedAtDescIdDesc(
            Long skillId, String skillVersion);
    default List<ReviewTask> findBySubmittedByAndSubjectTypeAndSubjectIdAndSubjectVersionOrderBySubmittedAtDescIdDesc(
            String submittedBy,
            ReviewSubjectType subjectType,
            Long subjectId,
            String subjectVersion) {
        throw new UnsupportedOperationException("Typed review subjects are not supported by this repository");
    }
    default List<ReviewTask> findBySubjectTypeAndSubjectIdAndSubjectVersionOrderBySubmittedAtDescIdDesc(
            ReviewSubjectType subjectType,
            Long subjectId,
            String subjectVersion) {
        throw new UnsupportedOperationException("Typed review subjects are not supported by this repository");
    }
    default List<ReviewTask> findByNamespaceIdAndSubmittedByAndStatus(
            Long namespaceId, String submittedBy, ReviewTaskStatus status) {
        return List.of();
    }
    /** Irreversibly cancels automatic eligibility of a still-pending task without a version bump. */
    default int cancelPendingAutoPublication(Long id) {
        return 0;
    }
    /** Marks a pending task as announced to normal reviewers; returns 1 only for the first caller. */
    default int markPendingManualReviewNotified(Long id) {
        return 0;
    }
    /** Records the structured decision method after the optimistic status transition succeeded. */
    default int recordDecision(Long id, ReviewDecisionMethod method, String scanTaskId) {
        return 0;
    }
    boolean existsByNamespaceId(Long namespaceId);
    void deleteBySkillVersionIdIn(Collection<Long> skillVersionIds);
    void deleteBySkillId(Long skillId);
    void deleteBySubjectTypeAndSubjectId(ReviewSubjectType subjectType, Long subjectId);
    void delete(ReviewTask reviewTask);
    int updateStatusWithVersion(Long id, ReviewTaskStatus status, String reviewedBy,
                               String reviewComment, Integer expectedVersion);
}
