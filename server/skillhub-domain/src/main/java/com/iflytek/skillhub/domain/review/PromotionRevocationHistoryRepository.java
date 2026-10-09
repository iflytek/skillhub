package com.iflytek.skillhub.domain.review;

import java.time.Instant;

/** Detaches target foreign keys while preserving approved promotion records. */
public interface PromotionRevocationHistoryRepository {
    boolean lockTarget(Long targetSkillId);
    int detachTarget(Long targetSkillId, String reviewerId, Instant revokedAt);
}
