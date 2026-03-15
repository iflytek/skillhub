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

@Repository
public interface SkillJpaRepository extends JpaRepository<Skill, Long>, SkillRepository {
    List<Skill> findByIdIn(List<Long> ids);
    Optional<Skill> findByNamespaceIdAndSlug(Long namespaceId, String slug);

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

    List<Skill> findBySlug(String slug);

    @Query("SELECT s FROM Skill s JOIN Namespace n ON s.namespaceId = n.id WHERE n.slug = :namespaceSlug AND s.slug = :slug")
    Optional<Skill> findByNamespaceSlugAndSlug(@Param("namespaceSlug") String namespaceSlug, @Param("slug") String slug);
}
