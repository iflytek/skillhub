package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
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
        PUBLISHED
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

        if (preference == Preference.CURRENT_USER) {
            return ownSkill.or(() -> publishedSkill)
                    .orElseThrow(() -> new DomainBadRequestException("error.skill.notFound", slug));
        }

        return publishedSkill.or(() -> ownSkill)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.notFound", slug));
    }
}
