package com.iflytek.skillhub.service.bundle;

import com.iflytek.skillhub.domain.bundle.SkillBundleDraftService;
import com.iflytek.skillhub.domain.bundle.SkillBundleException;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import org.springframework.stereotype.Component;

/**
 * Resolves bundle item snapshots from the existing skill aggregate. The snapshot
 * is captured at draft-build time so the bundle stays reproducible.
 */
@Component
public class JpaSkillBundleItemSourceResolver implements SkillBundleDraftService.SkillBundleItemSourceResolver {

    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final NamespaceRepository namespaceRepository;

    public JpaSkillBundleItemSourceResolver(SkillRepository skillRepository,
                                            SkillVersionRepository skillVersionRepository,
                                            NamespaceRepository namespaceRepository) {
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.namespaceRepository = namespaceRepository;
    }

    @Override
    public SkillBundleDraftService.SkillBundleItemSnapshot resolveRegistryItem(Long skillId, Long skillVersionId) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new SkillBundleException("error.skillBundle.item.skillNotFound"));
        SkillVersion version = skillVersionRepository.findById(skillVersionId)
                .orElseThrow(() -> new SkillBundleException("error.skillBundle.item.versionNotFound"));
        if (version.getStatus() != SkillVersionStatus.PUBLISHED) {
            throw new SkillBundleException("error.skillBundle.item.versionNotPublished");
        }
        Namespace namespace = namespaceRepository.findById(skill.getNamespaceId())
                .orElseThrow(() -> new SkillBundleException("error.skillBundle.item.namespaceNotFound"));
        return new SkillBundleDraftService.SkillBundleItemSnapshot(
                namespace.getSlug(),
                skill.getSlug(),
                skill.getDisplayName(),
                version.getVersion(),
                skill.getSummary(),
                version.getPublishedAt() != null ? version.getPublishedAt() : version.getCreatedAt()
        );
    }
}
