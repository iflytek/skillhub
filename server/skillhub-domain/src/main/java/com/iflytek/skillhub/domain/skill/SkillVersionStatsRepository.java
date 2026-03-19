package com.iflytek.skillhub.domain.skill;

import java.util.Optional;

/**
 * Domain repository contract for per-version counters such as download statistics.
 */
public interface SkillVersionStatsRepository {
    Optional<SkillVersionStats> findBySkillVersionId(Long skillVersionId);
    void incrementDownloadCount(Long skillVersionId, Long skillId);
}
