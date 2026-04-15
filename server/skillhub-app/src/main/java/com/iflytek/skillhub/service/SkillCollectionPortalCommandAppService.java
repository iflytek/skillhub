package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.collection.SkillCollection;
import com.iflytek.skillhub.domain.collection.SkillCollectionContributor;
import com.iflytek.skillhub.domain.collection.SkillCollectionContributorService;
import com.iflytek.skillhub.domain.collection.SkillCollectionDomainService;
import com.iflytek.skillhub.domain.collection.SkillCollectionMember;
import com.iflytek.skillhub.domain.collection.SkillCollectionMemberRepository;
import com.iflytek.skillhub.domain.collection.SkillCollectionMembershipService;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.dto.collection.SkillCollectionContributorResponse;
import com.iflytek.skillhub.dto.collection.SkillCollectionCreateRequest;
import com.iflytek.skillhub.dto.collection.SkillCollectionMemberResponse;
import com.iflytek.skillhub.dto.collection.SkillCollectionResponse;
import com.iflytek.skillhub.dto.collection.SkillCollectionUpdateRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillCollectionPortalCommandAppService {
    private final SkillCollectionDomainService skillCollectionDomainService;
    private final SkillCollectionMembershipService skillCollectionMembershipService;
    private final SkillCollectionContributorService skillCollectionContributorService;
    private final SkillCollectionMemberRepository memberRepository;

    public SkillCollectionPortalCommandAppService(
            SkillCollectionDomainService skillCollectionDomainService,
            SkillCollectionMembershipService skillCollectionMembershipService,
            SkillCollectionContributorService skillCollectionContributorService,
            SkillCollectionMemberRepository memberRepository
    ) {
        this.skillCollectionDomainService = skillCollectionDomainService;
        this.skillCollectionMembershipService = skillCollectionMembershipService;
        this.skillCollectionContributorService = skillCollectionContributorService;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public SkillCollectionResponse create(String actingUserId,
                                          boolean adminEquivalent,
                                          String targetOwnerIdOrNull,
                                          SkillCollectionCreateRequest req) {
        SkillCollection created = skillCollectionDomainService.createCollection(
                actingUserId,
                req.title(),
                req.description(),
                SkillVisibility.valueOf(req.visibility()),
                req.slug(),
                adminEquivalent,
                adminEquivalent ? targetOwnerIdOrNull : actingUserId
        );
        return SkillCollectionResponse.from(created);
    }

    @Transactional
    public SkillCollectionResponse updateMetadata(Long id,
                                                  String actingUserId,
                                                  boolean adminEquivalent,
                                                  SkillCollectionUpdateRequest req) {
        SkillCollection updated = skillCollectionDomainService.updateMetadata(
                id,
                actingUserId,
                req.title(),
                req.description(),
                req.slug(),
                adminEquivalent
        );
        return SkillCollectionResponse.from(updated, listMembers(id));
    }

    @Transactional
    public SkillCollectionResponse setVisibility(Long id, String actingUserId, boolean adminEquivalent, SkillVisibility vis) {
        SkillCollection updated = skillCollectionDomainService.setVisibility(id, actingUserId, vis, adminEquivalent);
        return SkillCollectionResponse.from(updated, listMembers(id));
    }

    @Transactional
    public void delete(Long id, String actingUserId, boolean adminEquivalent) {
        skillCollectionDomainService.deleteCollection(id, actingUserId, adminEquivalent);
    }

    @Transactional
    public SkillCollectionMemberResponse addSkill(Long id, String actingUserId, boolean adminEquivalent, Long skillId) {
        SkillCollectionMember member = skillCollectionMembershipService.addSkill(id, actingUserId, skillId, adminEquivalent);
        return SkillCollectionMemberResponse.from(member);
    }

    @Transactional
    public void removeSkill(Long id, String actingUserId, boolean adminEquivalent, Long skillId) {
        skillCollectionMembershipService.removeSkill(id, actingUserId, skillId, adminEquivalent);
    }

    @Transactional
    public List<SkillCollectionMemberResponse> reorder(Long id, String actingUserId, boolean adminEquivalent, List<Long> order) {
        skillCollectionMembershipService.reorderSkills(id, actingUserId, order, adminEquivalent);
        return listMembers(id);
    }

    @Transactional(readOnly = true)
    public List<SkillCollectionContributorResponse> listContributors(Long id, String actingUserId, boolean adminEquivalent) {
        return skillCollectionContributorService.listContributors(id, actingUserId, adminEquivalent).stream()
                .map(SkillCollectionContributorResponse::from)
                .toList();
    }

    @Transactional
    public SkillCollectionContributorResponse addContributor(Long id,
                                                             String actingUserId,
                                                             boolean adminEquivalent,
                                                             String contributorUserId) {
        SkillCollectionContributor contributor = skillCollectionContributorService.addContributor(
                id, actingUserId, contributorUserId, adminEquivalent);
        return SkillCollectionContributorResponse.from(contributor);
    }

    @Transactional
    public void removeContributor(Long id, String actingUserId, boolean adminEquivalent, String contributorUserId) {
        skillCollectionContributorService.removeContributor(id, actingUserId, contributorUserId, adminEquivalent);
    }

    private List<SkillCollectionMemberResponse> listMembers(Long collectionId) {
        return memberRepository.findByCollectionIdOrderBySortOrderAscIdAsc(collectionId).stream()
                .map(SkillCollectionMemberResponse::from)
                .toList();
    }
}
