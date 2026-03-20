package com.iflytek.skillhub.domain.social;

import java.util.Optional;

/**
 * Domain repository contract for per-user ratings and rating aggregates on one skill.
 */
public interface SkillRatingRepository {
    SkillRating save(SkillRating rating);
    Optional<SkillRating> findBySkillIdAndUserId(Long skillId, String userId);
    double averageScoreBySkillId(Long skillId);
    int countBySkillId(Long skillId);
    void deleteBySkillId(Long skillId);
}
