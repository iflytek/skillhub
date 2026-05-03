package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.skill.SkillVersionStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data repository for persisted per-version statistics rows.
 */
@Repository
public interface SkillVersionStatsJpaRepository extends JpaRepository<SkillVersionStats, Long> {

    @Modifying
    @Transactional
    @Query(
            value = """
                    UPDATE skill_version_stats
                    SET download_count = download_count + 1,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE skill_version_id = :skillVersionId
                    """,
            nativeQuery = true
    )
    int incrementExistingDownloadCount(@Param("skillVersionId") Long skillVersionId);

    @Modifying
    @Transactional
    @Query(
            value = """
                    INSERT INTO skill_version_stats (skill_version_id, skill_id, download_count, updated_at)
                    SELECT :skillVersionId, :skillId, 1, CURRENT_TIMESTAMP
                    WHERE NOT EXISTS (
                        SELECT 1
                        FROM skill_version_stats
                        WHERE skill_version_id = :skillVersionId
                    )
                    """,
            nativeQuery = true
    )
    int insertInitialDownloadCount(@Param("skillVersionId") Long skillVersionId, @Param("skillId") Long skillId);

    @Modifying
    @Transactional
    @Query("DELETE FROM SkillVersionStats s WHERE s.skillId = :skillId")
    void deleteBySkillId(@Param("skillId") Long skillId);
}
