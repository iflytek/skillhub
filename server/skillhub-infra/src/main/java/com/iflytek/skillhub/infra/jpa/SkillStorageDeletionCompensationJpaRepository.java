package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.skill.SkillStorageDeletionCompensation;
import com.iflytek.skillhub.domain.skill.SkillStorageDeletionCompensationStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SkillStorageDeletionCompensationJpaRepository
        extends JpaRepository<SkillStorageDeletionCompensation, Long> {

    @Query(
            value = """
                    SELECT *
                    FROM skill_storage_delete_compensation
                    WHERE status = :status
                    ORDER BY created_at ASC
                    LIMIT 100
                    """,
            nativeQuery = true
    )
    List<SkillStorageDeletionCompensation> findTop100ByStatusOrderByCreatedAtAsc(
            @Param("status") SkillStorageDeletionCompensationStatus status);
}
