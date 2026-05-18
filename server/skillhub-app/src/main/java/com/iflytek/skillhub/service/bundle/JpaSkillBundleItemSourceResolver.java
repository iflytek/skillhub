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

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        if (!skill.getId().equals(version.getSkillId())) {
            throw new SkillBundleException("error.skillBundle.item.versionNotFound");
        }
        if (version.getStatus() != SkillVersionStatus.PUBLISHED) {
            throw new SkillBundleException("error.skillBundle.item.versionNotPublished");
        }
        Namespace namespace = namespaceRepository.findById(skill.getNamespaceId())
                .orElseThrow(() -> new SkillBundleException("error.skillBundle.item.namespaceNotFound"));
        return toSnapshot(skill, version, namespace);
    }

    @Override
    public List<SkillBundleDraftService.SkillBundleItemSnapshot> resolveRegistryItems(
            List<SkillBundleDraftService.DraftItem> items) {
        List<Long> skillIds = items.stream().map(SkillBundleDraftService.DraftItem::skillId).distinct().toList();
        List<Long> versionIds = items.stream().map(SkillBundleDraftService.DraftItem::skillVersionId).distinct().toList();

        Map<Long, Skill> skillsById = skillRepository.findByIdIn(skillIds).stream()
                .collect(Collectors.toMap(Skill::getId, Function.identity()));
        Map<Long, SkillVersion> versionsById = skillVersionRepository.findByIdIn(versionIds).stream()
                .collect(Collectors.toMap(SkillVersion::getId, Function.identity()));
        List<Long> namespaceIds = skillsById.values().stream().map(Skill::getNamespaceId).distinct().toList();
        Map<Long, Namespace> namespacesById = namespaceRepository.findByIdIn(namespaceIds).stream()
                .collect(Collectors.toMap(Namespace::getId, Function.identity()));

        return items.stream().map(item -> {
            Skill skill = skillsById.get(item.skillId());
            if (skill == null) {
                throw new SkillBundleException("error.skillBundle.item.skillNotFound");
            }
            SkillVersion version = versionsById.get(item.skillVersionId());
            if (version == null || !skill.getId().equals(version.getSkillId())) {
                throw new SkillBundleException("error.skillBundle.item.versionNotFound");
            }
            if (version.getStatus() != SkillVersionStatus.PUBLISHED) {
                throw new SkillBundleException("error.skillBundle.item.versionNotPublished");
            }
            Namespace namespace = namespacesById.get(skill.getNamespaceId());
            if (namespace == null) {
                throw new SkillBundleException("error.skillBundle.item.namespaceNotFound");
            }
            return toSnapshot(skill, version, namespace);
        }).toList();
    }

    private SkillBundleDraftService.SkillBundleItemSnapshot toSnapshot(Skill skill,
                                                                       SkillVersion version,
                                                                       Namespace namespace) {
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
