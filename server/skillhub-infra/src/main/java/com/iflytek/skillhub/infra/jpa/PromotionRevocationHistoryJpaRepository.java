package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.review.PromotionRevocationHistoryRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.springframework.stereotype.Repository;

/** Native update is required because historical target FKs must be cleared before deleting versions. */
@Repository
public class PromotionRevocationHistoryJpaRepository implements PromotionRevocationHistoryRepository {
    private final EntityManager entityManager;

    public PromotionRevocationHistoryJpaRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public boolean lockTarget(Long targetSkillId) {
        return !entityManager.createNativeQuery("SELECT id FROM skill WHERE id = :id FOR UPDATE")
                .setParameter("id", targetSkillId).getResultList().isEmpty();
    }

    @Override
    public int detachTarget(Long targetSkillId, String reviewerId, Instant revokedAt) {
        entityManager.flush();
        int count = entityManager.createNativeQuery("""
                UPDATE promotion_request
                   SET target_skill_id_snapshot = target_skill_id,
                       target_version_id_snapshot = target_version_id,
                       target_skill_id = NULL,
                       target_version_id = NULL,
                       revoked_at = :revokedAt,
                       revoked_by = :reviewerId,
                       status = CASE WHEN status = 'PENDING' THEN 'REJECTED' ELSE status END,
                       reviewed_by = CASE WHEN status = 'PENDING' THEN :reviewerId ELSE reviewed_by END,
                       reviewed_at = CASE WHEN status = 'PENDING' THEN :revokedAt ELSE reviewed_at END,
                       review_comment = CASE WHEN status = 'PENDING'
                           THEN 'Target promotion revoked during review' ELSE review_comment END,
                       version = version + 1
                 WHERE target_skill_id = :targetSkillId
                """)
                .setParameter("targetSkillId", targetSkillId)
                .setParameter("reviewerId", reviewerId)
                .setParameter("revokedAt", revokedAt)
                .executeUpdate();
        entityManager.clear();
        return count;
    }
}
