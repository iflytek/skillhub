package com.iflytek.skillhub.domain.skill;

import java.util.List;
import java.util.Optional;

public interface SkillRepository {
    Optional<Skill> findById(Long id);
    List<Skill> findByIdIn(List<Long> ids);
    List<Skill> findAll();
    Optional<Skill> findByNamespaceIdAndSlug(Long namespaceId, String slug);
    List<Skill> findByNamespaceIdAndStatus(Long namespaceId, SkillStatus status);
    Skill save(Skill skill);
    List<Skill> findByOwnerId(String ownerId);
    void incrementDownloadCount(Long skillId);
    List<Skill> findBySlug(String slug);
    Optional<Skill> findByNamespaceSlugAndSlug(String namespaceSlug, String slug);
}
