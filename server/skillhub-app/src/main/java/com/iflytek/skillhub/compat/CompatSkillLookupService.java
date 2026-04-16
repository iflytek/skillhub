package com.iflytek.skillhub.compat;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.skill.service.SkillSlugResolutionService;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Compatibility-side lookup helper that centralizes legacy slug resolution and
 * visibility-aware canonical skill loading.
 */
@Service
public class CompatSkillLookupService {

    private final SkillRepository skillRepository;
    private final NamespaceRepository namespaceRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillSlugResolutionService skillSlugResolutionService;
    private final VisibilityChecker visibilityChecker;

    public CompatSkillLookupService(SkillRepository skillRepository,
                                    NamespaceRepository namespaceRepository,
                                    SkillVersionRepository skillVersionRepository,
                                    SkillSlugResolutionService skillSlugResolutionService,
                                    VisibilityChecker visibilityChecker) {
        this.skillRepository = skillRepository;
        this.namespaceRepository = namespaceRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillSlugResolutionService = skillSlugResolutionService;
        this.visibilityChecker = visibilityChecker;
    }

    public CompatSkillContext findByLegacySlug(String slug) {
        Skill skill = skillRepository.findBySlug(slug).stream().findFirst()
                .orElseThrow(() -> new DomainNotFoundException("error.skill.notFound", slug));
        Namespace namespace = namespaceRepository.findById(skill.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("error.namespace.notFound", skill.getNamespaceId()));
        return new CompatSkillContext(namespace, skill, findLatestVersion(skill));
    }

    public boolean canAccess(Skill skill, String currentUserId, Map<Long, NamespaceRole> userNsRoles) {
        if (skill == null) {
            return false;
        }
        Map<Long, NamespaceRole> roles = userNsRoles != null ? userNsRoles : Map.of();
        return visibilityChecker.canAccess(skill, currentUserId, roles);
    }

    public CompatSkillContext resolveVisible(String namespaceSlug, String skillSlug, String currentUserId) {
        return resolveVisible(namespaceSlug, skillSlug, currentUserId, Map.of());
    }

    public CompatSkillContext resolveVisible(String namespaceSlug,
                                             String skillSlug,
                                             String currentUserId,
                                             Map<Long, NamespaceRole> userNsRoles) {
        Namespace namespace = namespaceRepository.findBySlug(namespaceSlug)
                .orElseThrow(() -> new DomainNotFoundException("error.namespace.notFound", namespaceSlug));
        Skill skill = resolveVisibleSkill(namespace.getId(), skillSlug, currentUserId);
        if (!canAccess(skill, currentUserId, userNsRoles)) {
            throw new DomainNotFoundException("error.skill.notFound", skillSlug);
        }
        return new CompatSkillContext(namespace, skill, findLatestVersion(skill));
    }

    public Optional<SkillVersion> findVersion(Long skillId, String version) {
        if (skillId == null || version == null || version.isBlank()) {
            return Optional.empty();
        }
        return skillVersionRepository.findBySkillIdAndVersion(skillId, version);
    }

    public Optional<SkillVersion> findLatestVersion(Skill skill) {
        if (skill == null || skill.getLatestVersionId() == null) {
            return Optional.empty();
        }
        return skillVersionRepository.findById(skill.getLatestVersionId());
    }

    private Skill resolveVisibleSkill(Long namespaceId, String slug, String currentUserId) {
        try {
            return skillSlugResolutionService.resolve(
                    namespaceId,
                    slug,
                    currentUserId,
                    SkillSlugResolutionService.Preference.PUBLISHED
            );
        } catch (com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException ex) {
            throw new DomainNotFoundException("error.skill.notFound", slug);
        }
    }

    public record CompatSkillContext(Namespace namespace, Skill skill, Optional<SkillVersion> latestVersion) {
    }
}
