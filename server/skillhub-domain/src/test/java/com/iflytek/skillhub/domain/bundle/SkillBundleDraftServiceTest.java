package com.iflytek.skillhub.domain.bundle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

/**
 * Tests for {@link SkillBundleDraftService} validation and persistence rules.
 */
@ExtendWith(MockitoExtension.class)
class SkillBundleDraftServiceTest {

    private SkillBundleRepository bundleRepository;
    private SkillBundleVersionRepository versionRepository;
    private SkillBundleItemRepository itemRepository;
    private SkillBundleDraftService.SkillBundleItemSourceResolver resolver;
    private SkillBundleDraftService service;

    @BeforeEach
    void setUp() {
        bundleRepository = mock(SkillBundleRepository.class);
        versionRepository = mock(SkillBundleVersionRepository.class);
        itemRepository = mock(SkillBundleItemRepository.class);
        resolver = mock(SkillBundleDraftService.SkillBundleItemSourceResolver.class);
        service = new SkillBundleDraftService(bundleRepository, versionRepository, itemRepository, resolver);
    }

    @Test
    void buildDraft_rejectsWhenItemsEmpty() {
        SkillBundleDraftService.BuildDraftCommand cmd = command(SkillBundleType.CUSTOM, List.of(), List.of(), List.of());

        assertThatThrownBy(() -> service.buildDraft(cmd, "alice"))
                .isInstanceOf(SkillBundleException.class)
                .hasMessage("error.skillBundle.item.empty");

        verify(bundleRepository, never()).save(any());
    }

    @Test
    void buildDraft_rejectsProjectBundleWithoutProjectTypes() {
        SkillBundleDraftService.BuildDraftCommand cmd = command(
                SkillBundleType.PROJECT, List.of(), List.of(),
                List.of(item(1L, 11L, true, 10)));

        assertThatThrownBy(() -> service.buildDraft(cmd, "alice"))
                .isInstanceOf(SkillBundleException.class)
                .hasMessage("error.skillBundle.projectTypes.required");
    }

    @Test
    void buildDraft_rejectsRoleBundleWithoutRoleTags() {
        SkillBundleDraftService.BuildDraftCommand cmd = command(
                SkillBundleType.ROLE, List.of(), List.of(),
                List.of(item(1L, 11L, true, 10)));

        assertThatThrownBy(() -> service.buildDraft(cmd, "alice"))
                .isInstanceOf(SkillBundleException.class)
                .hasMessage("error.skillBundle.roleTags.required");
    }

    @Test
    void buildDraft_rejectsDuplicateItems() {
        SkillBundleDraftService.BuildDraftCommand cmd = command(
                SkillBundleType.CUSTOM, List.of(), List.of(),
                List.of(item(1L, 11L, true, 10), item(1L, 11L, false, 20)));

        assertThatThrownBy(() -> service.buildDraft(cmd, "alice"))
                .isInstanceOf(SkillBundleException.class)
                .hasMessage("error.skillBundle.item.duplicate");
    }

    @Test
    void buildDraft_rejectsConflictingVersionSlug() {
        SkillBundleDraftService.BuildDraftCommand cmd = command(
                SkillBundleType.CUSTOM, List.of(), List.of(),
                List.of(item(1L, 11L, true, 10)));

        SkillBundle existing = bundleStub();
        given(bundleRepository.findByNamespaceIdAndSlug(5L, "ops")).willReturn(Optional.of(existing));
        given(versionRepository.findByBundleIdAndVersion(99L, "1.0.0"))
                .willReturn(Optional.of(new SkillBundleVersion(99L, "1.0.0", 1, "{}", "{}", "key")));

        assertThatThrownBy(() -> service.buildDraft(cmd, "alice"))
                .isInstanceOf(SkillBundleException.class)
                .hasMessage("error.skillBundle.version.duplicate");
    }

    @Test
    void buildDraft_persistsBundleVersionAndItemsWithSnapshot() {
        SkillBundleDraftService.BuildDraftCommand cmd = command(
                SkillBundleType.CUSTOM, List.of(), List.of(),
                List.of(item(1L, 11L, true, 10)));

        given(bundleRepository.findByNamespaceIdAndSlug(5L, "ops")).willReturn(Optional.empty());
        given(bundleRepository.save(any(SkillBundle.class))).willAnswer(invocation -> {
            SkillBundle b = invocation.getArgument(0);
            setField(b, "id", 99L);
            return b;
        });
        given(versionRepository.findByBundleIdAndVersion(anyLong(), any())).willReturn(Optional.empty());
        given(versionRepository.save(any(SkillBundleVersion.class))).willAnswer(invocation -> {
            SkillBundleVersion v = invocation.getArgument(0);
            setField(v, "id", 120L);
            return v;
        });
        given(resolver.resolveRegistryItems(List.of(item(1L, 11L, true, 10)))).willReturn(
                List.of(new SkillBundleDraftService.SkillBundleItemSnapshot(
                        "global", "code-review", "Code Review", "1.3.0",
                        "Audit interface boundaries.", Instant.parse("2026-05-01T00:00:00Z"))));

        SkillBundleVersion saved = service.buildDraft(cmd, "alice");

        assertThat(saved.getId()).isEqualTo(120L);
        assertThat(saved.getManifestJson()).contains("\"coordinate\":\"@global/code-review\"");
        assertThat(saved.getManifestJson()).contains("\"roleDescription\":\"role\"");
        assertThat(saved.getLockJson()).contains("\"skillVersionId\":11");
        verify(itemRepository).save(any(SkillBundleItem.class));
        verify(resolver, times(1)).resolveRegistryItems(List.of(item(1L, 11L, true, 10)));
    }

    private SkillBundleDraftService.BuildDraftCommand command(SkillBundleType type,
                                                              List<String> projectTypes,
                                                              List<String> roleTags,
                                                              List<SkillBundleDraftService.DraftItem> items) {
        return new SkillBundleDraftService.BuildDraftCommand(
                5L, "ops", "Ops", "summary", "1.0.0", 1L,
                type, projectTypes, roleTags, items, "key");
    }

    private SkillBundleDraftService.DraftItem item(Long skillId, Long versionId, boolean required, int order) {
        return new SkillBundleDraftService.DraftItem(skillId, versionId, "role", required, order);
    }

    private SkillBundle bundleStub() {
        SkillBundle bundle = new SkillBundle(5L, "ops", "Ops", "summary", SkillBundleType.CUSTOM, "alice", "alice");
        setField(bundle, "id", 99L);
        return bundle;
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
