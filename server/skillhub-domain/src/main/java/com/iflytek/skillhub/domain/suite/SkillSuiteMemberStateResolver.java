package com.iflytek.skillhub.domain.suite;

import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Resolves live Skill state for immutable Suite member references without coordinate relinking. */
@Service
public class SkillSuiteMemberStateResolver {

    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final NamespaceRepository namespaceRepository;
    private final VisibilityChecker visibilityChecker;

    public SkillSuiteMemberStateResolver(
            SkillRepository skillRepository,
            SkillVersionRepository skillVersionRepository,
            NamespaceRepository namespaceRepository,
            VisibilityChecker visibilityChecker
    ) {
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.namespaceRepository = namespaceRepository;
        this.visibilityChecker = visibilityChecker;
    }

    public List<SkillSuiteMemberState> resolve(List<SkillSuiteVersionMember> members) {
        return resolve(members, skill -> false);
    }

    /** Resolves live member state and applies the canonical Skill visibility policy for this viewer. */
    public List<SkillSuiteMemberState> resolveForViewer(
            List<SkillSuiteVersionMember> members,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        return resolve(members, skill -> visibilityChecker.canAccess(
                skill, userId, namespaceRoles, platformRoles));
    }

    private List<SkillSuiteMemberState> resolve(
            List<SkillSuiteVersionMember> members,
            Predicate<Skill> viewerAccess
    ) {
        List<Long> versionIds = members.stream()
                .map(SkillSuiteVersionMember::getSkillVersionId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, SkillVersion> versions = skillVersionRepository.findByIdIn(versionIds).stream()
                .collect(Collectors.toMap(SkillVersion::getId, Function.identity()));

        List<Long> skillIds = versions.values().stream()
                .map(SkillVersion::getSkillId)
                .distinct()
                .toList();
        Map<Long, Skill> skills = skillRepository.findByIdIn(skillIds).stream()
                .collect(Collectors.toMap(Skill::getId, Function.identity()));

        List<Long> namespaceIds = skills.values().stream()
                .map(Skill::getNamespaceId)
                .distinct()
                .toList();
        Map<Long, Namespace> namespaces = namespaceRepository.findByIdIn(namespaceIds).stream()
                .collect(Collectors.toMap(Namespace::getId, Function.identity()));

        return members.stream()
                .map(member -> resolveOne(member, versions, skills, namespaces, viewerAccess))
                .toList();
    }

    private SkillSuiteMemberState resolveOne(
            SkillSuiteVersionMember member,
            Map<Long, SkillVersion> versions,
            Map<Long, Skill> skills,
            Map<Long, Namespace> namespaces,
            Predicate<Skill> viewerAccess
    ) {
        SkillVersion version = versions.get(member.getSkillVersionId());
        if (version == null || !java.util.Objects.equals(version.getSkillId(), member.getSkillId())) {
            return SkillSuiteMemberState.deleted(member.getSkillId(), member.getSkillVersionId());
        }
        Skill skill = skills.get(version.getSkillId());
        if (skill == null) {
            return SkillSuiteMemberState.deleted(member.getSkillId(), member.getSkillVersionId());
        }
        Namespace namespace = namespaces.get(skill.getNamespaceId());
        if (namespace == null) {
            return SkillSuiteMemberState.deleted(member.getSkillId(), member.getSkillVersionId());
        }
        return new SkillSuiteMemberState(
                skill.getId(), version.getId(), skill.getNamespaceId(), skill.getDisplayName(), skill.getSummary(),
                namespace.getStatus(), skill.getVisibility(),
                skill.getStatus(), skill.isHidden(), version.getStatus(), version.isDownloadReady(),
                version.getYankedAt() != null, viewerAccess.test(skill));
    }
}
