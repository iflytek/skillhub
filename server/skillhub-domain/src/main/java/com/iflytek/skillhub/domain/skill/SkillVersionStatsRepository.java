package com.iflytek.skillhub.domain.skill;

import java.util.Optional;

public interface SkillVersionStatsRepository {
    Optional<SkillVersionStats> findBySkillVersionId(Long skillVersionId);
    void incrementDownloadCount(Long skillVersionId, Long skillId);
}
