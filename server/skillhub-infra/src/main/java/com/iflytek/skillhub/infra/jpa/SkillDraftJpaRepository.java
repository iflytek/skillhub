package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.authoring.SkillDraft;
import com.iflytek.skillhub.domain.authoring.SkillDraftRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * JPA-backed repository for skill authoring drafts.
 */
@Repository
public interface SkillDraftJpaRepository extends JpaRepository<SkillDraft, Long>, SkillDraftRepository {

    List<SkillDraft> findByOwnerIdOrderByUpdatedAtDesc(String ownerId);

    List<SkillDraft> findByNamespaceId(Long namespaceId);

    Optional<SkillDraft> findByOwnerIdAndNameIgnoreCase(String ownerId, String name);

    boolean existsByIdAndOwnerId(Long id, String ownerId);
}
