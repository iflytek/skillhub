package com.iflytek.skillhub.domain.authoring;

import java.util.List;
import java.util.Optional;

/**
 * Domain repository contract for skill authoring drafts.
 */
public interface SkillDraftRepository {

    SkillDraft save(SkillDraft draft);

    Optional<SkillDraft> findById(Long id);

    List<SkillDraft> findByOwnerIdOrderByUpdatedAtDesc(String ownerId);

    List<SkillDraft> findByNamespaceId(Long namespaceId);

    /** Case-insensitive uniqueness of (owner, name) used at creation time. */
    Optional<SkillDraft> findByOwnerIdAndNameIgnoreCase(String ownerId, String name);

    void delete(SkillDraft draft);

    boolean existsByIdAndOwnerId(Long id, String ownerId);
}
