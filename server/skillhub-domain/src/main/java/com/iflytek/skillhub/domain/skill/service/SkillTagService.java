package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SkillTagService {

    private static final String RESERVED_TAG_LATEST = "latest";

    private final NamespaceRepository namespaceRepository;
    private final NamespaceMemberRepository namespaceMemberRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillTagRepository skillTagRepository;
    private final VisibilityChecker visibilityChecker;

    public SkillTagService(
            NamespaceRepository namespaceRepository,
            NamespaceMemberRepository namespaceMemberRepository,
            SkillRepository skillRepository,
            SkillVersionRepository skillVersionRepository,
            SkillTagRepository skillTagRepository,
            VisibilityChecker visibilityChecker) {
        this.namespaceRepository = namespaceRepository;
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillTagRepository = skillTagRepository;
        this.visibilityChecker = visibilityChecker;
    }

    public List<SkillTag> listTags(String namespaceSlug,
                                   String skillSlug,
                                   String currentUserId,
                                   java.util.Map<Long, NamespaceRole> userNamespaceRoles) {
        Namespace namespace = findNamespace(namespaceSlug);
        Skill skill = skillRepository.findByNamespaceIdAndSlug(namespace.getId(), skillSlug)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.notFound", skillSlug));
        if (!visibilityChecker.canAccess(skill, currentUserId, userNamespaceRoles)) {
            throw new DomainForbiddenException("error.skill.access.denied", skillSlug);
        }

        List<SkillTag> tags = new java.util.ArrayList<>(skillTagRepository.findBySkillId(skill.getId()));
        if (skill.getLatestVersionId() != null) {
            tags.add(new SkillTag(skill.getId(), RESERVED_TAG_LATEST, skill.getLatestVersionId(), skill.getOwnerId()));
        }
        return tags;
    }

    @Transactional
    public SkillTag createOrMoveTag(
            String namespaceSlug,
            String skillSlug,
            String tagName,
            String targetVersion,
            String operatorId) {

        // Reject "latest" tag
        if (RESERVED_TAG_LATEST.equalsIgnoreCase(tagName)) {
            throw new DomainBadRequestException("error.skill.tag.latest.reserved");
        }

        Namespace namespace = findNamespace(namespaceSlug);
        assertAdminOrOwner(namespace.getId(), operatorId);
        Skill skill = skillRepository.findByNamespaceIdAndSlug(namespace.getId(), skillSlug)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.notFound", skillSlug));

        // Find target version
        SkillVersion version = skillVersionRepository.findBySkillIdAndVersion(skill.getId(), targetVersion)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.version.notFound", targetVersion));

        // Target must be PUBLISHED
        if (version.getStatus() != SkillVersionStatus.PUBLISHED) {
            throw new DomainBadRequestException("error.skill.tag.targetVersion.notPublished");
        }

        // Check if tag exists
        SkillTag existingTag = skillTagRepository.findBySkillIdAndTagName(skill.getId(), tagName).orElse(null);

        if (existingTag != null) {
            // Move tag
            existingTag.setVersionId(version.getId());
            return skillTagRepository.save(existingTag);
        } else {
            // Create new tag
            SkillTag newTag = new SkillTag(skill.getId(), tagName, version.getId(), operatorId);
            return skillTagRepository.save(newTag);
        }
    }

    @Transactional
    public void deleteTag(String namespaceSlug, String skillSlug, String tagName, String operatorId) {
        // Reject "latest" tag
        if (RESERVED_TAG_LATEST.equalsIgnoreCase(tagName)) {
            throw new DomainBadRequestException("error.skill.tag.latest.delete");
        }

        Namespace namespace = findNamespace(namespaceSlug);
        assertAdminOrOwner(namespace.getId(), operatorId);
        Skill skill = skillRepository.findByNamespaceIdAndSlug(namespace.getId(), skillSlug)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.notFound", skillSlug));

        SkillTag tag = skillTagRepository.findBySkillIdAndTagName(skill.getId(), tagName)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.tag.notFound", tagName));

        skillTagRepository.delete(tag);
    }

    private Namespace findNamespace(String slug) {
        return namespaceRepository.findBySlug(slug)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.slug.notFound", slug));
    }

    private void assertAdminOrOwner(Long namespaceId, String operatorId) {
        NamespaceRole role = namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, operatorId)
                .map(member -> member.getRole())
                .orElseThrow(() -> new DomainForbiddenException("error.namespace.membership.required"));
        if (role != NamespaceRole.OWNER && role != NamespaceRole.ADMIN) {
            throw new DomainForbiddenException("error.namespace.admin.required");
        }
    }
}
