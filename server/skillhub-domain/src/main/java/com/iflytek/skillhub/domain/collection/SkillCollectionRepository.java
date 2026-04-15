package com.iflytek.skillhub.domain.collection;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Domain repository contract for persisted skill collection aggregates.
 */
public interface SkillCollectionRepository {

    Optional<SkillCollection> findById(Long id);

    Optional<SkillCollection> findByOwnerIdAndSlug(String ownerId, String slug);

    List<SkillCollection> findByIdIn(Collection<Long> ids);

    Page<SkillCollection> findByOwnerIdOrIdIn(String ownerId, Collection<Long> ids, Pageable pageable);

    long countByOwnerId(String ownerId);

    boolean existsByOwnerIdAndSlug(String ownerId, String slug);

    boolean existsByOwnerIdAndSlugAndIdNot(String ownerId, String slug, Long id);

    SkillCollection save(SkillCollection collection);

    void delete(SkillCollection collection);
}
