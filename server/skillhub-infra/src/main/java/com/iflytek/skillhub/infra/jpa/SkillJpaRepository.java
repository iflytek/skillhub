package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.SkillAvatarCandidate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import jakarta.persistence.LockModeType;

/**
 * Base Spring Data JPA repository for persisted skill aggregates and common skill queries.
 */
@Repository
public interface SkillJpaRepository extends JpaRepository<Skill, Long>, SkillRepository {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Skill s WHERE s.id = :id")
    Optional<Skill> findLockedById(@Param("id") Long id);
    List<Skill> findByIdIn(List<Long> ids);
    @Query("""
            SELECT s FROM Skill s WHERE s.id > :afterId AND s.status = :activeStatus AND s.activeAvatarId IS NOT NULL
              AND ((s.activeAvatarAudience = :privateVisibility
                    AND s.visibility IN (:teamVisibility, :publicVisibility))
                OR (s.activeAvatarAudience = :teamVisibility AND s.visibility = :publicVisibility))
              AND NOT EXISTS (SELECT c.id FROM SkillAvatarCandidate c WHERE c.id = s.pendingAvatarId
                AND c.audience = s.visibility AND c.status = :pendingStatus
                AND (c.machineStatus NOT IN (:pendingMachine, :processingMachine, :errorMachine)
                  OR (c.machineStatus = :pendingMachine AND c.createdAt > :staleBefore)
                  OR (c.machineStatus = :processingMachine AND c.machineLeaseUntil > :now)
                  OR (c.machineStatus = :errorMachine AND (c.machineAttemptCount >= :maxAutoAttempts
                    OR c.machineNextRetryAt > :now))))
              AND NOT EXISTS (SELECT c.id FROM SkillAvatarCandidate c WHERE c.skillId = s.id
                AND c.audience = s.visibility AND c.scopeReviewVersionId = s.latestVersionId
                AND c.status = :rejectedStatus)
            ORDER BY s.id ASC
            """)
    List<Skill> findScopeReviewRecoveryCandidates(@Param("afterId") Long afterId,
            @Param("activeStatus") SkillStatus activeStatus,
            @Param("privateVisibility") SkillVisibility privateVisibility,
            @Param("teamVisibility") SkillVisibility teamVisibility,
            @Param("publicVisibility") SkillVisibility publicVisibility,
            @Param("pendingStatus") SkillAvatarCandidate.Status pendingStatus,
            @Param("pendingMachine") SkillAvatarCandidate.MachineStatus pendingMachine,
            @Param("processingMachine") SkillAvatarCandidate.MachineStatus processingMachine,
            @Param("errorMachine") SkillAvatarCandidate.MachineStatus errorMachine,
            @Param("maxAutoAttempts") int maxAutoAttempts,
            @Param("rejectedStatus") SkillAvatarCandidate.Status rejectedStatus,
            @Param("staleBefore") Instant staleBefore,
            @Param("now") Instant now,
            Pageable pageable);
    List<Skill> findByNamespaceIdAndSlug(Long namespaceId, String slug);
    List<Skill> findByNamespaceIdInAndSlugIn(List<Long> namespaceIds, List<String> slugs);
    Optional<Skill> findByNamespaceIdAndSlugAndOwnerId(Long namespaceId, String slug, String ownerId);
    boolean existsByNamespaceId(Long namespaceId);

    @Override
    default List<Skill> findByNamespaceIdAndStatus(Long namespaceId, SkillStatus status) {
        return findByNamespaceIdAndStatusOrderByCreatedAtDesc(namespaceId, status);
    }

    List<Skill> findByNamespaceIdAndStatusOrderByCreatedAtDesc(Long namespaceId, SkillStatus status);
    Page<Skill> findByNamespaceIdAndStatus(Long namespaceId, SkillStatus status, Pageable pageable);
    List<Skill> findByOwnerId(String ownerId);

    @Modifying
    @Transactional
    @Query("UPDATE Skill s SET s.downloadCount = s.downloadCount + 1 WHERE s.id = :skillId")
    void incrementDownloadCount(@Param("skillId") Long skillId);

    @Modifying
    @Transactional
    @Query("UPDATE Skill s SET s.subscriptionCount = s.subscriptionCount + 1 WHERE s.id = :skillId")
    void incrementSubscriptionCount(@Param("skillId") Long skillId);

    @Modifying
    @Transactional
    @Query("UPDATE Skill s SET s.subscriptionCount = CASE WHEN s.subscriptionCount > 0 THEN s.subscriptionCount - 1 ELSE 0 END WHERE s.id = :skillId")
    void decrementSubscriptionCount(@Param("skillId") Long skillId);

    List<Skill> findBySlug(String slug);

    @Query("SELECT s FROM Skill s JOIN Namespace n ON s.namespaceId = n.id WHERE n.slug = :namespaceSlug AND s.slug = :slug")
    List<Skill> findByNamespaceSlugAndSlug(@Param("namespaceSlug") String namespaceSlug, @Param("slug") String slug);
}
