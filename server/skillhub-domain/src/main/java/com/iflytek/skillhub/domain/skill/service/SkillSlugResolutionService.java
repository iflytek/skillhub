package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillTagRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Resolves ambiguous namespace-slug pairs to the most appropriate skill record for the caller.
 */
@Service
public class SkillSlugResolutionService {

    public enum Preference {
        CURRENT_USER,
        PUBLISHED,
        PUBLIC_PUBLISHED
    }

    private final SkillRepository skillRepository;

    public SkillSlugResolutionService(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    public Skill resolve(Long namespaceId, String slug, String currentUserId, Preference preference) {
        List<Skill> skills = skillRepository.findByNamespaceIdAndSlug(namespaceId, slug);
        if (skills.isEmpty()) {
            throw new DomainBadRequestException("error.skill.notFound", slug);
        }

        Optional<Skill> ownSkill = currentUserId == null
                ? Optional.empty()
                : skills.stream().filter(skill -> currentUserId.equals(skill.getOwnerId())).findFirst();
        Optional<Skill> publishedSkill = skills.stream()
                .filter(skill -> skill.getLatestVersionId() != null && !skill.isHidden())
                .findFirst();
        Optional<Skill> publicPublishedSkill = skills.stream()
                .filter(skill -> skill.getLatestVersionId() != null
                        && !skill.isHidden()
                        && skill.getVisibility() == SkillVisibility.PUBLIC)
                .findFirst();

        if (preference == Preference.PUBLIC_PUBLISHED && publicPublishedSkill.isPresent()) {
            return publicPublishedSkill.orElseThrow();
        }
        if (preference != Preference.PUBLISHED) {
            return ownSkill.or(() -> publishedSkill)
                    .orElseThrow(() -> new DomainBadRequestException("error.skill.notFound", slug));
        }

        return publishedSkill.or(() -> ownSkill)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.notFound", slug));
    }

    /**
     * Resolves a skill for an exact version read. Public callers use the published
     * skill first; its owner's matching draft remains visible through the same URL.
     */
    public Skill resolveForVersion(
            Long namespaceId,
            String slug,
            String version,
            String currentUserId,
            Preference preference,
            SkillVersionRepository versionRepository) {
        Skill resolved = resolve(namespaceId, slug, currentUserId, preference);
        if (preference != Preference.PUBLIC_PUBLISHED || currentUserId == null) {
            return resolved;
        }
        boolean publishedMatch = versionRepository.findBySkillIdAndVersion(resolved.getId(), version)
                .map(candidate -> candidate.getStatus() == com.iflytek.skillhub.domain.skill.SkillVersionStatus.PUBLISHED)
                .orElse(false);
        if (publishedMatch || currentUserId.equals(resolved.getOwnerId())) {
            return resolved;
        }
        return skillRepository.findByNamespaceIdAndSlug(namespaceId, slug).stream()
                .filter(skill -> currentUserId.equals(skill.getOwnerId()))
                .filter(skill -> versionRepository.findBySkillIdAndVersion(skill.getId(), version).isPresent())
                .findFirst()
                .orElse(resolved);
    }

    public Skill resolveForTag(
            Long namespaceId,
            String slug,
            String tagName,
            String currentUserId,
            Preference preference,
            SkillTagRepository tagRepository) {
        Skill resolved = resolve(namespaceId, slug, currentUserId, preference);
        if (preference != Preference.PUBLIC_PUBLISHED || currentUserId == null
                || tagRepository.findBySkillIdAndTagName(resolved.getId(), tagName).isPresent()
                || currentUserId.equals(resolved.getOwnerId())) {
            return resolved;
        }
        return skillRepository.findByNamespaceIdAndSlug(namespaceId, slug).stream()
                .filter(skill -> currentUserId.equals(skill.getOwnerId()))
                .filter(skill -> tagRepository.findBySkillIdAndTagName(skill.getId(), tagName).isPresent())
                .findFirst()
                .orElse(resolved);
    }
}
