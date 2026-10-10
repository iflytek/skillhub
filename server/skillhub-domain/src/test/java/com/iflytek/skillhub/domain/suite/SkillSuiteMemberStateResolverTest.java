package com.iflytek.skillhub.domain.suite;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillSuiteMemberStateResolverTest {

    @Test
    void appliesCanonicalSkillVisibilityRulesToMemberBrowsing() {
        SkillRepository skillRepository = mock(SkillRepository.class);
        SkillVersionRepository versionRepository = mock(SkillVersionRepository.class);
        NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
        SkillSuiteMemberStateResolver resolver = new SkillSuiteMemberStateResolver(
                skillRepository, versionRepository, namespaceRepository, new VisibilityChecker());

        Namespace namespace = new Namespace("team", "Team", "owner");
        Skill skill = new Skill(1L, "private-helper", "skill-owner", SkillVisibility.PRIVATE);
        skill.setDisplayName("Private Helper");
        skill.setSummary("Private metadata");
        skill.setLatestVersionId(40L);
        SkillVersion version = new SkillVersion(30L, "1.0.0", "skill-owner");
        version.setStatus(SkillVersionStatus.PUBLISHED);
        version.setDownloadReady(true);
        setId(namespace, 1L);
        setId(skill, 30L);
        setId(version, 40L);

        SkillSuiteVersionMember member = new SkillSuiteVersionMember(
                20L,
                new SkillSuiteMemberSelection(
                        30L, 40L, "team", "private-helper", "1.0.0", "sha256:private"),
                0,
                true);
        when(versionRepository.findByIdIn(List.of(40L))).thenReturn(List.of(version));
        when(skillRepository.findByIdIn(List.of(30L))).thenReturn(List.of(skill));
        when(namespaceRepository.findByIdIn(List.of(1L))).thenReturn(List.of(namespace));

        SkillSuiteMemberState ordinaryMember = resolver.resolveForViewer(
                List.of(member), "suite-author", Map.of(1L, NamespaceRole.MEMBER), Set.of()).getFirst();
        SkillSuiteMemberState skillOwner = resolver.resolveForViewer(
                List.of(member), "skill-owner", Map.of(1L, NamespaceRole.MEMBER), Set.of()).getFirst();

        assertThat(ordinaryMember.viewerCanRead()).isFalse();
        assertThat(skillOwner.viewerCanRead()).isTrue();
    }

    private void setId(Object target, Long id) {
        try {
            var field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }
}
