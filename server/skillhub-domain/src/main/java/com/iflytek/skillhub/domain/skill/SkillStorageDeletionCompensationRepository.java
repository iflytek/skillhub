package com.iflytek.skillhub.domain.skill;

import java.util.List;

public interface SkillStorageDeletionCompensationRepository {
    SkillStorageDeletionCompensation save(SkillStorageDeletionCompensation compensation);
    List<SkillStorageDeletionCompensation> findTop100ByStatusOrderByCreatedAtAsc(SkillStorageDeletionCompensationStatus status);
}
