package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Base Spring Data JPA repository for persisted skill aggregates and common skill queries.
 */
@Repository
public interface SkillJpaRepository extends JpaRepository<Skill, Long>, SkillRepository {
    List<Skill> findByIdIn(List<Long> ids);
    List<Skill> findByNamespaceIdAndSlug(Long namespaceId, String slug);
    Optional<Skill> findByNamespaceIdAndSlugAndOwnerId(Long namespaceId, String slug, String ownerId);

    @Override
    default List<Skill> findByNamespaceIdAndStatus(Long namespaceId, SkillStatus status) {
        return findByNamespaceIdAndStatusOrderByCreatedAtDesc(namespaceId, status);
    }

    List<Skill> findByNamespaceIdAndStatusOrderByCreatedAtDesc(Long namespaceId, SkillStatus status);
    Page<Skill> findByNamespaceIdAndStatus(Long namespaceId, SkillStatus status, Pageable pageable);
    List<Skill> findByOwnerId(String ownerId);
    Page<Skill> findByOwnerIdOrderByUpdatedAtDesc(String ownerId, Pageable pageable);

    @Override
    default Page<Skill> findByOwnerId(String ownerId, Pageable pageable) {
        return findByOwnerIdOrderByUpdatedAtDesc(ownerId, pageable);
    }

    @Modifying
    @Transactional
    @Query("UPDATE Skill s SET s.downloadCount = s.downloadCount + 1 WHERE s.id = :skillId")
    void incrementDownloadCount(@Param("skillId") Long skillId);

    List<Skill> findBySlug(String slug);

    @Query("SELECT s FROM Skill s JOIN Namespace n ON s.namespaceId = n.id WHERE n.slug = :namespaceSlug AND s.slug = :slug")
    Optional<Skill> findByNamespaceSlugAndSlug(@Param("namespaceSlug") String namespaceSlug, @Param("slug") String slug);
}
