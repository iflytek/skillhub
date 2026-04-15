package com.iflytek.skillhub.domain.collection;

import java.util.List;

/**
 * Domain repository contract for collection contributor grants.
 */
public interface SkillCollectionContributorRepository {

    boolean existsByCollectionIdAndUserId(Long collectionId, String userId);

    List<SkillCollectionContributor> findByUserId(String userId);

    long countByCollectionId(Long collectionId);

    void deleteByCollectionIdAndUserId(Long collectionId, String userId);

    List<SkillCollectionContributor> findByCollectionId(Long collectionId);

    SkillCollectionContributor save(SkillCollectionContributor contributor);

    void delete(SkillCollectionContributor contributor);
}
