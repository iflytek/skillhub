package com.iflytek.skillhub.domain.skill;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Domain repository contract for loading and persisting skill aggregates and common read models.
 */
public interface SkillRepository {
    Optional<Skill> findById(Long id);
    List<Skill> findByIdIn(List<Long> ids);
    List<Skill> findAll();
    List<Skill> findByNamespaceIdAndSlug(Long namespaceId, String slug);
    Optional<Skill> findByNamespaceIdAndSlugAndOwnerId(Long namespaceId, String slug, String ownerId);
    List<Skill> findByNamespaceIdAndStatus(Long namespaceId, SkillStatus status);
    Skill save(Skill skill);
    void delete(Skill skill);
    List<Skill> findByOwnerId(String ownerId);
    Page<Skill> findByOwnerId(String ownerId, Pageable pageable);
    void incrementDownloadCount(Long skillId);
    List<Skill> findBySlug(String slug);
    Optional<Skill> findByNamespaceSlugAndSlug(String namespaceSlug, String slug);
}
