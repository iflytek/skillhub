package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.collection.SkillCollection;
import com.iflytek.skillhub.domain.collection.SkillCollectionContributorRepository;
import com.iflytek.skillhub.domain.collection.SkillCollectionMemberRepository;
import com.iflytek.skillhub.domain.collection.SkillCollectionRepository;
import com.iflytek.skillhub.domain.collection.SkillReadableForActorPort;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.dto.collection.SkillCollectionMemberResponse;
import com.iflytek.skillhub.dto.collection.SkillCollectionResponse;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillCollectionPortalQueryAppService {
    private final SkillCollectionRepository collectionRepository;
    private final SkillCollectionContributorRepository contributorRepository;
    private final SkillCollectionMemberRepository memberRepository;
    private final SkillReadableForActorPort skillReadableForActorPort;

    public SkillCollectionPortalQueryAppService(
            SkillCollectionRepository collectionRepository,
            SkillCollectionContributorRepository contributorRepository,
            SkillCollectionMemberRepository memberRepository,
            SkillReadableForActorPort skillReadableForActorPort
    ) {
        this.collectionRepository = collectionRepository;
        this.contributorRepository = contributorRepository;
        this.memberRepository = memberRepository;
        this.skillReadableForActorPort = skillReadableForActorPort;
    }

    @Transactional(readOnly = true)
    public Page<SkillCollectionResponse> listMine(Pageable pageable, String actingUserId, boolean adminEquivalent) {
        if (adminEquivalent) {
            return collectionRepository.findByOwnerIdOrIdIn(actingUserId, List.of(-1L), pageable)
                    .map(collection -> buildCollectionResponse(collection, actingUserId, false, false));
        }

        Set<Long> contributorCollectionIds = contributorRepository.findByUserId(actingUserId).stream()
                .map(c -> c.getCollectionId())
                .collect(java.util.stream.Collectors.toSet());
        List<Long> ids = contributorCollectionIds.isEmpty() ? List.of(-1L) : List.copyOf(contributorCollectionIds);

        return collectionRepository.findByOwnerIdOrIdIn(actingUserId, ids, pageable)
                .map(collection -> {
                    boolean contributor = !actingUserId.equals(collection.getOwnerId())
                            && contributorCollectionIds.contains(collection.getId());
                    return buildCollectionResponse(collection, actingUserId, contributor, true);
                });
    }

    @Transactional(readOnly = true)
    public SkillCollectionResponse getForActor(Long id,
                                               String actingUserId,
                                               boolean adminEquivalent,
                                               boolean anonymous) {
        SkillCollection collection = collectionRepository.findById(id)
                .orElseThrow(() -> new DomainBadRequestException("error.skillCollection.notFound", id));

        boolean owner = false;
        boolean contributor = false;
        if (!anonymous && actingUserId != null) {
            owner = actingUserId.equals(collection.getOwnerId());
            contributor = !owner && contributorRepository.existsByCollectionIdAndUserId(collection.getId(), actingUserId);
        }

        if (anonymous && collection.getVisibility() != SkillVisibility.PUBLIC) {
            throw new DomainBadRequestException("error.skillCollection.notFound", id);
        }

        if (!anonymous && !adminEquivalent) {
            if (!owner && !contributor && collection.getVisibility() != SkillVisibility.PUBLIC) {
                throw new DomainBadRequestException("error.skillCollection.notFound", id);
            }
        }

        String viewer = anonymous ? null : actingUserId;
        return buildCollectionResponse(collection, viewer, contributor, !anonymous && !adminEquivalent);
    }

    @Transactional(readOnly = true)
    public SkillCollectionResponse getPublicByOwnerAndSlug(String ownerId, String slug, String viewerUserIdOrNull) {
        SkillCollection collection = collectionRepository.findByOwnerIdAndSlug(ownerId, slug)
                .orElseThrow(() -> new DomainBadRequestException("error.skillCollection.notFound", slug));
        if (collection.getVisibility() != SkillVisibility.PUBLIC) {
            throw new DomainBadRequestException("error.skillCollection.notFound", slug);
        }
        return buildCollectionResponse(collection, viewerUserIdOrNull, false, false);
    }

    private List<SkillCollectionMemberResponse> listVisibleMembers(Long collectionId, String viewerUserIdOrNull) {
        return memberRepository.findByCollectionIdOrderBySortOrderAscIdAsc(collectionId).stream()
                .filter(member -> skillReadableForActorPort.canActingUserReadSkill(viewerUserIdOrNull, member.getSkillId()))
                .map(SkillCollectionMemberResponse::from)
                .toList();
    }

    private SkillCollectionResponse buildCollectionResponse(
            SkillCollection collection,
            String viewerUserIdOrNull,
            boolean contributor,
            boolean includeContributorHiddenCount
    ) {
        List<SkillCollectionMemberResponse> visibleMembers = listVisibleMembers(collection.getId(), viewerUserIdOrNull);
        int hiddenCount = 0;
        if (includeContributorHiddenCount && contributor) {
            long totalMembers = memberRepository.countByCollectionId(collection.getId());
            long hiddenMembers = Math.max(0L, totalMembers - visibleMembers.size());
            hiddenCount = hiddenMembers > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) hiddenMembers;
        }
        return SkillCollectionResponse.from(collection, visibleMembers, hiddenCount);
    }
}
