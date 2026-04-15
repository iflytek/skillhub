package com.iflytek.skillhub.domain.collection;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contributor grants on a skill collection (Phase 1 — no HTTP).
 */
@Service
public class SkillCollectionContributorService {

    private final SkillCollectionRepository skillCollectionRepository;
    private final SkillCollectionContributorRepository contributorRepository;
    private final SkillCollectionAuthorizationPolicy authorizationPolicy;

    public SkillCollectionContributorService(SkillCollectionRepository skillCollectionRepository,
                                             SkillCollectionContributorRepository contributorRepository,
                                             SkillCollectionAuthorizationPolicy authorizationPolicy) {
        this.skillCollectionRepository = skillCollectionRepository;
        this.contributorRepository = contributorRepository;
        this.authorizationPolicy = authorizationPolicy;
    }

    @Transactional
    public SkillCollectionContributor addContributor(Long collectionId,
                                                     String actingUserId,
                                                     String contributorUserId,
                                                     boolean adminEquivalent) {
        SkillCollection collection = requireCollection(collectionId);
        boolean owner = collection.getOwnerId().equals(actingUserId);
        boolean contributor = !owner
                && contributorRepository.existsByCollectionIdAndUserId(collectionId, actingUserId);
        authorizationPolicy.checkAllowed(owner, contributor, adminEquivalent,
                SkillCollectionAuthorizationPolicy.CollectionOperation.ADD_REMOVE_CONTRIBUTOR);
        if (contributorUserId == null || contributorUserId.isBlank()) {
            throw new DomainBadRequestException("error.skillCollection.contributor.userIdBlank");
        }
        String uid = contributorUserId.trim();
        if (uid.equals(collection.getOwnerId())) {
            throw new DomainBadRequestException("error.skillCollection.contributor.ownerNotAllowed");
        }
        if (contributorRepository.existsByCollectionIdAndUserId(collectionId, uid)) {
            throw new DomainBadRequestException("error.skillCollection.contributor.duplicate", uid);
        }
        if (contributorRepository.countByCollectionId(collectionId) >= SkillCollectionLimits.MAX_CONTRIBUTORS_PER_COLLECTION) {
            throw new DomainBadRequestException("error.skillCollection.cap.contributorsPerCollection");
        }
        return contributorRepository.save(new SkillCollectionContributor(collectionId, uid));
    }

    @Transactional(readOnly = true)
    public List<SkillCollectionContributor> listContributors(Long collectionId, String actingUserId, boolean adminEquivalent) {
        SkillCollection collection = requireCollection(collectionId);
        boolean owner = collection.getOwnerId().equals(actingUserId);
        boolean contributor = !owner
                && contributorRepository.existsByCollectionIdAndUserId(collectionId, actingUserId);
        authorizationPolicy.checkAllowed(owner, contributor, adminEquivalent,
                SkillCollectionAuthorizationPolicy.CollectionOperation.ADD_REMOVE_CONTRIBUTOR);
        return contributorRepository.findByCollectionId(collectionId);
    }

    @Transactional
    public void removeContributor(Long collectionId, String actingUserId, String contributorUserId, boolean adminEquivalent) {
        SkillCollection collection = requireCollection(collectionId);
        boolean owner = collection.getOwnerId().equals(actingUserId);
        boolean contributor = !owner
                && contributorRepository.existsByCollectionIdAndUserId(collectionId, actingUserId);
        authorizationPolicy.checkAllowed(owner, contributor, adminEquivalent,
                SkillCollectionAuthorizationPolicy.CollectionOperation.ADD_REMOVE_CONTRIBUTOR);
        if (contributorUserId == null || contributorUserId.isBlank()) {
            throw new DomainBadRequestException("error.skillCollection.contributor.userIdBlank");
        }
        if (!contributorRepository.existsByCollectionIdAndUserId(collectionId, contributorUserId.trim())) {
            throw new DomainBadRequestException("error.skillCollection.contributor.notFound", contributorUserId);
        }
        contributorRepository.deleteByCollectionIdAndUserId(collectionId, contributorUserId.trim());
    }

    private SkillCollection requireCollection(Long collectionId) {
        return skillCollectionRepository.findById(collectionId)
                .orElseThrow(() -> new DomainBadRequestException("error.skillCollection.notFound", collectionId));
    }
}
