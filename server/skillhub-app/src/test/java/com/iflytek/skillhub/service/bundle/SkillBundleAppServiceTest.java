package com.iflytek.skillhub.service.bundle;

import com.iflytek.skillhub.domain.bundle.SkillBundleDraftService;
import com.iflytek.skillhub.domain.bundle.SkillBundleItemRepository;
import com.iflytek.skillhub.domain.bundle.SkillBundleRepository;
import com.iflytek.skillhub.domain.bundle.SkillBundleReviewService;
import com.iflytek.skillhub.domain.bundle.SkillBundleType;
import com.iflytek.skillhub.domain.bundle.SkillBundleVersion;
import com.iflytek.skillhub.domain.bundle.SkillBundleVersionRepository;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.dto.bundle.BuildSkillBundleDraftRequest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SkillBundleAppServiceTest {

    @Test
    void buildDraft_padsMissingSemverSegmentsForStableVersionOrdering() {
        SkillBundleDraftService draftService = mock(SkillBundleDraftService.class);
        SkillBundleReviewService reviewService = mock(SkillBundleReviewService.class);
        SkillBundleRepository bundleRepository = mock(SkillBundleRepository.class);
        SkillBundleVersionRepository versionRepository = mock(SkillBundleVersionRepository.class);
        SkillBundleItemRepository itemRepository = mock(SkillBundleItemRepository.class);
        NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
        SkillBundleAppService service = new SkillBundleAppService(
                draftService, reviewService, bundleRepository, versionRepository,
                itemRepository, namespaceRepository,
                Clock.fixed(Instant.parse("2026-05-18T00:00:00Z"), ZoneOffset.UTC));

        Namespace namespace = new Namespace("team-a", "Team A", "alice");
        setField(namespace, "id", 5L);
        given(namespaceRepository.findBySlug("team-a")).willReturn(Optional.of(namespace));
        given(draftService.buildDraft(any(SkillBundleDraftService.BuildDraftCommand.class), any()))
                .willAnswer(invocation -> new SkillBundleVersion(99L, "1.2", 0L, "{}", "{}", "key"));

        service.buildDraft("team-a", request("1.2"), "alice");
        service.buildDraft("team-a", request("1.1.9"), "alice");

        var captor = org.mockito.ArgumentCaptor.forClass(SkillBundleDraftService.BuildDraftCommand.class);
        verify(draftService, org.mockito.Mockito.times(2))
                .buildDraft(captor.capture(), org.mockito.ArgumentMatchers.eq("alice"));
        long oneTwo = captor.getAllValues().get(0).versionSort();
        long oneOneNine = captor.getAllValues().get(1).versionSort();

        assertThat(oneTwo).isEqualTo(1_000_002_000_000L);
        assertThat(oneTwo).isGreaterThan(oneOneNine);
    }

    private BuildSkillBundleDraftRequest request(String version) {
        return new BuildSkillBundleDraftRequest(
                "ops", "Ops", version, SkillBundleType.CUSTOM, "summary",
                List.of(), List.of(),
                List.of(new BuildSkillBundleDraftRequest.DraftItemRequest(1L, 11L, "role", true, 10)),
                List.of());
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
