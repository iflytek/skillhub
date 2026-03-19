package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.skill.SkillVersionStats;
import com.iflytek.skillhub.domain.skill.SkillVersionStatsRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA-backed repository for per-version statistics, including atomic download counter increments.
 */
@Repository
public interface SkillVersionStatsJpaRepository extends JpaRepository<SkillVersionStats, Long>, SkillVersionStatsRepository {

    @Override
    default Optional<SkillVersionStats> findBySkillVersionId(Long skillVersionId) {
        return findById(skillVersionId);
    }

    @Override
    @Modifying
    @Transactional
    @Query(
            value = """
                    INSERT INTO skill_version_stats (skill_version_id, skill_id, download_count, updated_at)
                    VALUES (:skillVersionId, :skillId, 1, CURRENT_TIMESTAMP)
                    ON CONFLICT (skill_version_id)
                    DO UPDATE SET download_count = skill_version_stats.download_count + 1,
                                  updated_at = CURRENT_TIMESTAMP
                    """,
            nativeQuery = true
    )
    void incrementDownloadCount(@Param("skillVersionId") Long skillVersionId, @Param("skillId") Long skillId);
}
