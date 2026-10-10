package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * JPA-backed repository for skill version history and status-oriented version queries.
 *
 * <p>The deletion lock uses explicit {@code FOR UPDATE} SQL because Hibernate's PostgreSQL dialect
 * emits {@code FOR NO KEY UPDATE}, which H2's PostgreSQL compatibility mode cannot execute. It
 * locks every version in stable ID order so concurrent deletions cannot both remove the last
 * versions of one skill.
 */
@Repository
public interface SkillVersionJpaRepository extends JpaRepository<SkillVersion, Long>, SkillVersionRepository {

    @Override
    @Query(value = "SELECT * FROM skill_version WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<SkillVersion> findByIdForUpdate(@Param("id") Long id);

    @Override
    @Query("SELECT version.status FROM SkillVersion version WHERE version.id = :id AND version.skillId = :skillId")
    Optional<SkillVersionStatus> findStatusByIdAndSkillId(@Param("id") Long id, @Param("skillId") Long skillId);
    List<SkillVersion> findByIdIn(List<Long> ids);
    List<SkillVersion> findBySkillId(Long skillId);
    List<SkillVersion> findBySkillIdIn(List<Long> skillIds);
    List<SkillVersion> findBySkillIdInAndStatusOrderByCreatedAtDesc(List<Long> skillIds, SkillVersionStatus status);
    List<SkillVersion> findBySkillIdInAndVersionIn(List<Long> skillIds, List<String> versions);
    Optional<SkillVersion> findBySkillIdAndVersion(Long skillId, String version);

    @Override
    @Query(value = """
        SELECT skill_version.*
        FROM skill_version
        WHERE skill_version.skill_id = :skillId
        ORDER BY skill_version.id
        FOR UPDATE
    """, nativeQuery = true)
    List<SkillVersion> findBySkillIdForUpdate(@Param("skillId") Long skillId);

    @Override
    default List<SkillVersion> findBySkillIdAndStatus(Long skillId, SkillVersionStatus status) {
        return findBySkillIdAndStatusOrderByCreatedAtDesc(skillId, status);
    }

    @Override
    default List<SkillVersion> findBySkillIdInAndStatus(List<Long> skillIds, SkillVersionStatus status) {
        return findBySkillIdInAndStatusOrderByCreatedAtDesc(skillIds, status);
    }

    List<SkillVersion> findBySkillIdAndStatusOrderByCreatedAtDesc(Long skillId, SkillVersionStatus status);
    Page<SkillVersion> findBySkillIdAndStatus(Long skillId, SkillVersionStatus status, Pageable pageable);
    void deleteBySkillId(Long skillId);
}
