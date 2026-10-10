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
    ReviewTask save(ReviewTask reviewTask);
    Optional<ReviewTask> findById(Long id);
    Optional<ReviewTask> findBySkillVersionIdAndStatus(Long skillVersionId, ReviewTaskStatus status);
    default Optional<ReviewTask> findBySubjectTypeAndSubjectVersionIdAndStatus(
            ReviewSubjectType subjectType, Long subjectVersionId, ReviewTaskStatus status) {
        throw new UnsupportedOperationException("Typed review subjects are not supported by this repository");
    }
    Page<ReviewTask> findByStatus(ReviewTaskStatus status, Pageable pageable);
    Page<ReviewTask> findByNamespaceIdAndStatus(Long namespaceId, ReviewTaskStatus status, Pageable pageable);
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
    boolean existsByNamespaceId(Long namespaceId);
    void deleteBySkillVersionIdIn(Collection<Long> skillVersionIds);
    void deleteBySkillId(Long skillId);
    void deleteBySubjectTypeAndSubjectId(ReviewSubjectType subjectType, Long subjectId);
    void delete(ReviewTask reviewTask);
    int updateStatusWithVersion(Long id, ReviewTaskStatus status, String reviewedBy,
                               String reviewComment, Integer expectedVersion);
}
