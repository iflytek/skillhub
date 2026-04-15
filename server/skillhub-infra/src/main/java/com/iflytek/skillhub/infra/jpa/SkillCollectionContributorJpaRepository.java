package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.collection.SkillCollectionContributor;
import com.iflytek.skillhub.domain.collection.SkillCollectionContributorRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * JPA-backed repository for skill collection contributor rows.
 */
@Repository
public interface SkillCollectionContributorJpaRepository
        extends JpaRepository<SkillCollectionContributor, Long>, SkillCollectionContributorRepository {
    @Override
    List<SkillCollectionContributor> findByUserId(String userId);

    @Override
    List<SkillCollectionContributor> findByCollectionId(Long collectionId);
}
