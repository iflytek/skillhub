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
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class JpaSkillBundleItemSourceResolverTest {

    @Test
    void resolveRegistryItems_usesBatchRepositoryReadsAndPreservesInputOrder() {
        SkillRepository skillRepository = mock(SkillRepository.class);
        SkillVersionRepository versionRepository = mock(SkillVersionRepository.class);
        NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
        JpaSkillBundleItemSourceResolver resolver =
                new JpaSkillBundleItemSourceResolver(skillRepository, versionRepository, namespaceRepository);
        Skill firstSkill = skill(1L, 5L, "alpha", "Alpha");
        Skill secondSkill = skill(2L, 6L, "beta", "Beta");
        SkillVersion firstVersion = version(11L, 1L, "1.0.0", SkillVersionStatus.PUBLISHED);
        SkillVersion secondVersion = version(22L, 2L, "2.0.0", SkillVersionStatus.PUBLISHED);
        Namespace firstNamespace = namespace(5L, "team-a");
        Namespace secondNamespace = namespace(6L, "team-b");
        given(skillRepository.findByIdIn(List.of(1L, 2L))).willReturn(List.of(firstSkill, secondSkill));
        given(versionRepository.findByIdIn(List.of(11L, 22L))).willReturn(List.of(firstVersion, secondVersion));
        given(namespaceRepository.findByIdIn(List.of(5L, 6L))).willReturn(List.of(firstNamespace, secondNamespace));

        List<SkillBundleDraftService.SkillBundleItemSnapshot> snapshots = resolver.resolveRegistryItems(List.of(
                new SkillBundleDraftService.DraftItem(1L, 11L, "first", true, 10),
                new SkillBundleDraftService.DraftItem(2L, 22L, "second", true, 20)
        ));

        assertThat(snapshots).extracting(SkillBundleDraftService.SkillBundleItemSnapshot::skillSlug)
                .containsExactly("alpha", "beta");
        verify(skillRepository).findByIdIn(List.of(1L, 2L));
        verify(versionRepository).findByIdIn(List.of(11L, 22L));
        verify(namespaceRepository).findByIdIn(List.of(5L, 6L));
        verify(skillRepository, never()).findById(1L);
        verify(versionRepository, never()).findById(11L);
        verify(namespaceRepository, never()).findById(5L);
    }

    @Test
    void resolveRegistryItems_rejectsVersionThatDoesNotBelongToSkill() {
        SkillRepository skillRepository = mock(SkillRepository.class);
        SkillVersionRepository versionRepository = mock(SkillVersionRepository.class);
        NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
        JpaSkillBundleItemSourceResolver resolver =
                new JpaSkillBundleItemSourceResolver(skillRepository, versionRepository, namespaceRepository);
        Skill skill = skill(1L, 5L, "alpha", "Alpha");
        SkillVersion version = version(22L, 2L, "2.0.0", SkillVersionStatus.PUBLISHED);
        given(skillRepository.findByIdIn(List.of(1L))).willReturn(List.of(skill));
        given(versionRepository.findByIdIn(List.of(22L))).willReturn(List.of(version));
        given(namespaceRepository.findByIdIn(List.of(5L))).willReturn(List.of(namespace(5L, "team-a")));

        assertThatThrownBy(() -> resolver.resolveRegistryItems(List.of(
                new SkillBundleDraftService.DraftItem(1L, 22L, "role", true, 10)
        )))
                .isInstanceOf(SkillBundleException.class)
                .hasMessage("error.skillBundle.item.versionNotFound");
    }

    private Skill skill(Long id, Long namespaceId, String slug, String displayName) {
        Skill skill = new Skill(namespaceId, slug, "alice", SkillVisibility.PUBLIC);
        skill.setDisplayName(displayName);
        skill.setSummary(displayName + " summary");
        setField(skill, "id", id);
        return skill;
    }

    private SkillVersion version(Long id, Long skillId, String value, SkillVersionStatus status) {
        SkillVersion version = new SkillVersion(skillId, value, "alice");
        version.setStatus(status);
        version.setPublishedAt(Instant.parse("2026-05-18T00:00:00Z"));
        setField(version, "id", id);
        return version;
    }

    private Namespace namespace(Long id, String slug) {
        Namespace namespace = new Namespace(slug, slug, "alice");
        setField(namespace, "id", id);
        return namespace;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
