package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.collection.SkillCollection;
import com.iflytek.skillhub.domain.collection.SkillCollectionRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * JPA-backed repository for skill collection aggregates.
 */
@Repository
public interface SkillCollectionJpaRepository extends JpaRepository<SkillCollection, Long>, SkillCollectionRepository {
    @Override
    Optional<SkillCollection> findByOwnerIdAndSlug(String ownerId, String slug);

    @Override
    List<SkillCollection> findByIdIn(Collection<Long> ids);

    @Override
    Page<SkillCollection> findByOwnerIdOrIdIn(String ownerId, Collection<Long> ids, Pageable pageable);
}
