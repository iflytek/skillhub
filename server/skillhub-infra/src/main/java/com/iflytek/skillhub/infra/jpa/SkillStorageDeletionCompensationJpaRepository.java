package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.skill.SkillStorageDeletionCompensation;
import com.iflytek.skillhub.domain.skill.SkillStorageDeletionCompensationStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface SkillStorageDeletionCompensationJpaRepository
        extends JpaRepository<SkillStorageDeletionCompensation, Long> {

    List<SkillStorageDeletionCompensation> findTop100ByStatusOrderByCreatedAtAsc(
            SkillStorageDeletionCompensationStatus status);
}
