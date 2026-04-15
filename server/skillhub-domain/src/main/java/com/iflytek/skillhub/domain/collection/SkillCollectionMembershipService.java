package com.iflytek.skillhub.domain.collection;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ordered skill membership in a collection (Phase 1 — no HTTP).
 */
@Service
public class SkillCollectionMembershipService {

    private final SkillCollectionRepository skillCollectionRepository;
    private final SkillCollectionMemberRepository memberRepository;
    private final SkillCollectionContributorRepository contributorRepository;
    private final SkillCollectionAuthorizationPolicy authorizationPolicy;
    private final SkillReadableForActorPort skillReadableForActorPort;

    public SkillCollectionMembershipService(SkillCollectionRepository skillCollectionRepository,
                                            SkillCollectionMemberRepository memberRepository,
                                            SkillCollectionContributorRepository contributorRepository,
                                            SkillCollectionAuthorizationPolicy authorizationPolicy,
                                            SkillReadableForActorPort skillReadableForActorPort) {
        this.skillCollectionRepository = skillCollectionRepository;
        this.memberRepository = memberRepository;
        this.contributorRepository = contributorRepository;
        this.authorizationPolicy = authorizationPolicy;
        this.skillReadableForActorPort = skillReadableForActorPort;
    }

    @Transactional
    public SkillCollectionMember addSkill(Long collectionId, String actingUserId, Long skillId, boolean adminEquivalent) {
        SkillCollection collection = requireCollection(collectionId);
        boolean owner = collection.getOwnerId().equals(actingUserId);
        boolean contributor = !owner
                && contributorRepository.existsByCollectionIdAndUserId(collectionId, actingUserId);
        authorizationPolicy.checkAllowed(owner, contributor, adminEquivalent,
                SkillCollectionAuthorizationPolicy.CollectionOperation.ADD_SKILL);
        if (!skillReadableForActorPort.canActingUserReadSkill(actingUserId, skillId)) {
            throw new DomainBadRequestException("error.skillCollection.skill.notReadable", skillId);
        }
        if (memberRepository.countByCollectionId(collectionId) >= SkillCollectionLimits.MAX_SKILLS_PER_COLLECTION) {
            throw new DomainBadRequestException("error.skillCollection.cap.skillsPerCollection");
        }
        if (memberRepository.existsByCollectionIdAndSkillId(collectionId, skillId)) {
            throw new DomainBadRequestException("error.skillCollection.member.duplicate", skillId);
        }
        int nextOrder = memberRepository.findByCollectionIdOrderBySortOrderAscIdAsc(collectionId).stream()
                .mapToInt(m -> m.getSortOrder() == null ? 0 : m.getSortOrder())
                .max()
                .orElse(-1) + 1;
        SkillCollectionMember row = new SkillCollectionMember(collectionId, skillId, nextOrder);
        return memberRepository.save(row);
    }

    @Transactional
    public void removeSkill(Long collectionId, String actingUserId, Long skillId, boolean adminEquivalent) {
        SkillCollection collection = requireCollection(collectionId);
        boolean owner = collection.getOwnerId().equals(actingUserId);
        boolean contributor = !owner
                && contributorRepository.existsByCollectionIdAndUserId(collectionId, actingUserId);
        authorizationPolicy.checkAllowed(owner, contributor, adminEquivalent,
                SkillCollectionAuthorizationPolicy.CollectionOperation.REMOVE_SKILL);
        if (!memberRepository.existsByCollectionIdAndSkillId(collectionId, skillId)) {
            throw new DomainBadRequestException("error.skillCollection.member.notFound", skillId);
        }
        memberRepository.deleteByCollectionIdAndSkillId(collectionId, skillId);
    }

    @Transactional
    public void reorderSkills(Long collectionId, String actingUserId, List<Long> orderedSkillIds, boolean adminEquivalent) {
        SkillCollection collection = requireCollection(collectionId);
        boolean owner = collection.getOwnerId().equals(actingUserId);
        boolean contributor = !owner
                && contributorRepository.existsByCollectionIdAndUserId(collectionId, actingUserId);
        authorizationPolicy.checkAllowed(owner, contributor, adminEquivalent,
                SkillCollectionAuthorizationPolicy.CollectionOperation.REORDER_SKILLS);
        List<SkillCollectionMember> existing = memberRepository.findByCollectionIdOrderBySortOrderAscIdAsc(collectionId);
        Set<Long> existingIds = existing.stream().map(SkillCollectionMember::getSkillId).collect(Collectors.toSet());
        if (orderedSkillIds == null || orderedSkillIds.isEmpty()) {
            throw new DomainBadRequestException("error.skillCollection.reorder.empty");
        }
        if (orderedSkillIds.stream().anyMatch(Objects::isNull)) {
            throw new DomainBadRequestException("error.skillCollection.reorder.nullId");
        }
        if (new HashSet<>(orderedSkillIds).size() != orderedSkillIds.size()) {
            throw new DomainBadRequestException("error.skillCollection.reorder.duplicateIds");
        }
        if (!existingIds.equals(Set.copyOf(orderedSkillIds))) {
            throw new DomainBadRequestException("error.skillCollection.reorder.setMismatch");
        }
        var bySkill = existing.stream().collect(Collectors.toMap(SkillCollectionMember::getSkillId, m -> m, (a, b) -> a));
        List<SkillCollectionMember> toSave = new ArrayList<>();
        int order = 0;
        for (Long skillId : orderedSkillIds) {
            SkillCollectionMember m = Objects.requireNonNull(bySkill.get(skillId), "member");
            m.setSortOrder(order++);
            toSave.add(m);
        }
        memberRepository.saveAll(toSave);
    }

    @Transactional
    public int reconcileInvisibleSkillsForCollection(long collectionId) {
        var collectionOptional = skillCollectionRepository.findById(collectionId);
        if (collectionOptional.isEmpty()) {
            return 0;
        }
        SkillCollection collection = collectionOptional.get();
        List<SkillCollectionMember> members = memberRepository.findByCollectionIdOrderBySortOrderAscIdAsc(collectionId);
        int deleted = 0;
        for (SkillCollectionMember member : members) {
            if (!skillReadableForActorPort.canActingUserReadSkill(collection.getOwnerId(), member.getSkillId())) {
                memberRepository.deleteByCollectionIdAndSkillId(collectionId, member.getSkillId());
                deleted++;
            }
        }
        return deleted;
    }

    private SkillCollection requireCollection(Long collectionId) {
        return skillCollectionRepository.findById(collectionId)
                .orElseThrow(() -> new DomainBadRequestException("error.skillCollection.notFound", collectionId));
    }
}
