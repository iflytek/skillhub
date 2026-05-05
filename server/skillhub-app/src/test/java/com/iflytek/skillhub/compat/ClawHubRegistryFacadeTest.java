package com.iflytek.skillhub.compat;

import com.iflytek.skillhub.compat.dto.ClawHubRegistrySearchResponse;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillLifecycleProjectionService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.SkillLifecycleVersionResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.service.SkillSearchAppService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClawHubRegistryFacadeTest {

    @Test
    void search_mapsInstantToEpochMillis() {
        CanonicalSlugMapper canonicalSlugMapper = new CanonicalSlugMapper();
        SkillSearchAppService skillSearchAppService = mock(SkillSearchAppService.class);
        SkillQueryService skillQueryService = mock(SkillQueryService.class);
        CompatSkillLookupService compatSkillLookupService = mock(CompatSkillLookupService.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);

        ClawHubRegistryFacade facade = new ClawHubRegistryFacade(
                canonicalSlugMapper,
                skillSearchAppService,
                skillQueryService,
                compatSkillLookupService,
                userAccountRepository
        );

        Instant updatedAt = Instant.parse("2026-03-18T09:00:00Z");
        when(skillSearchAppService.search("agent", null, "relevance", 0, 20, null, Map.of()))
                .thenReturn(new SkillSearchAppService.SearchResponse(
                        List.of(new SkillSummaryResponse(
                                1L,
                                "time-skill",
                                "Time Skill",
                                "summary",
                                "PUBLIC",
                                "ACTIVE",
                                12L,
                                3,
                                BigDecimal.valueOf(4.5),
                                2,
                                "global",
                                updatedAt,
                                false,
                                new SkillLifecycleVersionResponse(11L, "1.0.0", "PUBLISHED"),
                                new SkillLifecycleVersionResponse(11L, "1.0.0", "PUBLISHED"),
                                null,
                                "PUBLISHED"
                        )),
                        1,
                        0,
                        20
                ));

        ClawHubRegistrySearchResponse result = facade.search("agent", 20, null, Map.of());

        assertThat(result.results()).hasSize(1);
        assertThat(result.results().get(0).updatedAt())
                .isEqualTo(updatedAt.toEpochMilli());
    }

    @Test
    void resolveDownloadUrl_treatsBlankVersionAsNull() {
        CanonicalSlugMapper canonicalSlugMapper = new CanonicalSlugMapper();
        SkillSearchAppService skillSearchAppService = mock(SkillSearchAppService.class);
        SkillQueryService skillQueryService = mock(SkillQueryService.class);
        CompatSkillLookupService compatSkillLookupService = mock(CompatSkillLookupService.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        ClawHubRegistryFacade facade = new ClawHubRegistryFacade(
                canonicalSlugMapper,
                skillSearchAppService,
                skillQueryService,
                compatSkillLookupService,
                userAccountRepository
        );
        when(skillQueryService.resolveVersion("team-a", "demo", null, null, null, "user-1", Map.of()))
                .thenReturn(new SkillQueryService.ResolvedVersionDTO(1L, "team-a", "demo", "1.0.0", 2L, null, true, "/download"));

        String downloadUrl = facade.resolveDownloadUrl("team-a--demo", " ", "user-1", null);

        assertThat(downloadUrl).isEqualTo("/download");
    }

    @Test
    void getSkill_fallsBackToCanonicalSlugAndEmptyOwnerWhenDataMissing() {
        CanonicalSlugMapper canonicalSlugMapper = new CanonicalSlugMapper();
        SkillSearchAppService skillSearchAppService = mock(SkillSearchAppService.class);
        SkillQueryService skillQueryService = mock(SkillQueryService.class);
        CompatSkillLookupService compatSkillLookupService = mock(CompatSkillLookupService.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        ClawHubRegistryFacade facade = new ClawHubRegistryFacade(
                canonicalSlugMapper,
                skillSearchAppService,
                skillQueryService,
                compatSkillLookupService,
                userAccountRepository
        );

        Skill skill = new Skill(10L, "demo", "owner-1", SkillVisibility.PUBLIC);
        ReflectionTestUtils.setField(skill, "id", 99L);
        ReflectionTestUtils.setField(skill, "createdAt", Instant.parse("2026-03-18T08:00:00Z"));
        ReflectionTestUtils.setField(skill, "updatedAt", Instant.parse("2026-03-18T09:00:00Z"));
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        SkillLifecycleProjectionService.VersionProjection publishedProjection =
                new SkillLifecycleProjectionService.VersionProjection(77L, "1.0.0", "PUBLISHED");

        when(skillQueryService.getSkillDetail("team-a", "demo", "user-1", Map.of()))
                .thenReturn(new SkillQueryService.SkillDetailDTO(
                        99L, "demo", " ", "owner-1", null, "summary", "PUBLIC", "ACTIVE",
                        0L, 0, BigDecimal.ZERO, 0, false, 10L,
                        Instant.parse("2026-03-18T08:00:00Z"), Instant.parse("2026-03-18T09:00:00Z"),
                        false, false, false, false,
                        publishedProjection, publishedProjection, null, null, "PUBLISHED"
                ));
        when(compatSkillLookupService.resolveVisible("team-a", "demo", "user-1", Map.of()))
                .thenReturn(new CompatSkillLookupService.CompatSkillContext(namespace, skill, Optional.empty()));
        when(compatSkillLookupService.findVersion(99L, "1.0.0")).thenReturn(Optional.empty());
        when(userAccountRepository.findById("owner-1")).thenReturn(Optional.empty());

        var response = facade.getSkill("team-a--demo", "user-1", null);

        assertThat(response.skill().slug()).isEqualTo("team-a--demo");
        assertThat(response.skill().displayName()).isEqualTo("team-a--demo");
        assertThat(response.latestVersion().version()).isEqualTo("1.0.0");
        assertThat(response.latestVersion().createdAt()).isZero();
        assertThat(response.owner().displayName()).isNull();
    }

    @Test
    void search_clampsOversizedLimitToMaximum() {
        CanonicalSlugMapper canonicalSlugMapper = new CanonicalSlugMapper();
        SkillSearchAppService skillSearchAppService = mock(SkillSearchAppService.class);
        SkillQueryService skillQueryService = mock(SkillQueryService.class);
        CompatSkillLookupService compatSkillLookupService = mock(CompatSkillLookupService.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        ClawHubRegistryFacade facade = new ClawHubRegistryFacade(
                canonicalSlugMapper,
                skillSearchAppService,
                skillQueryService,
                compatSkillLookupService,
                userAccountRepository
        );
        when(skillSearchAppService.search("agent", null, "relevance", 0, 100, null, Map.of()))
                .thenReturn(new SkillSearchAppService.SearchResponse(List.of(), 0, 0, 100));

        ClawHubRegistrySearchResponse result = facade.search("agent", 999, null, null);

        assertThat(result.results()).isEmpty();
        verify(skillSearchAppService).search("agent", null, "relevance", 0, 100, null, Map.of());
    }

    @Test
    void search_clampsZeroOrNegativeLimitToDefault() {
        CanonicalSlugMapper canonicalSlugMapper = new CanonicalSlugMapper();
        SkillSearchAppService skillSearchAppService = mock(SkillSearchAppService.class);
        SkillQueryService skillQueryService = mock(SkillQueryService.class);
        CompatSkillLookupService compatSkillLookupService = mock(CompatSkillLookupService.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        ClawHubRegistryFacade facade = new ClawHubRegistryFacade(
                canonicalSlugMapper,
                skillSearchAppService,
                skillQueryService,
                compatSkillLookupService,
                userAccountRepository
        );
        when(skillSearchAppService.search("agent", null, "relevance", 0, 20, null, Map.of()))
                .thenReturn(new SkillSearchAppService.SearchResponse(List.of(), 0, 0, 20));

        ClawHubRegistrySearchResponse result = facade.search("agent", 0, null, null);

        assertThat(result.results()).isEmpty();
        verify(skillSearchAppService).search("agent", null, "relevance", 0, 20, null, Map.of());
    }

    @Test
    void search_withNullPublishedVersion_returnsNullVersion() {
        CanonicalSlugMapper canonicalSlugMapper = new CanonicalSlugMapper();
        SkillSearchAppService skillSearchAppService = mock(SkillSearchAppService.class);
        SkillQueryService skillQueryService = mock(SkillQueryService.class);
        CompatSkillLookupService compatSkillLookupService = mock(CompatSkillLookupService.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        ClawHubRegistryFacade facade = new ClawHubRegistryFacade(
                canonicalSlugMapper,
                skillSearchAppService,
                skillQueryService,
                compatSkillLookupService,
                userAccountRepository
        );
        when(skillSearchAppService.search("agent", null, "relevance", 0, 20, null, Map.of()))
                .thenReturn(new SkillSearchAppService.SearchResponse(
                        List.of(new SkillSummaryResponse(
                                1L, "time-skill", "Time Skill", "summary", "PUBLIC", "ACTIVE",
                                12L, 3, BigDecimal.valueOf(4.5), 2, "global",
                                Instant.parse("2026-03-18T09:00:00Z"), false,
                                null, null, null, null)),
                        1, 0, 20));

        ClawHubRegistrySearchResponse result = facade.search("agent", 20, null, Map.of());

        assertThat(result.results().get(0).version()).isNull();
    }

    @Test
    void getSkill_withNullPublishedVersion_returnsNullLatestVersion() {
        CanonicalSlugMapper canonicalSlugMapper = new CanonicalSlugMapper();
        SkillSearchAppService skillSearchAppService = mock(SkillSearchAppService.class);
        SkillQueryService skillQueryService = mock(SkillQueryService.class);
        CompatSkillLookupService compatSkillLookupService = mock(CompatSkillLookupService.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        ClawHubRegistryFacade facade = new ClawHubRegistryFacade(
                canonicalSlugMapper,
                skillSearchAppService,
                skillQueryService,
                compatSkillLookupService,
                userAccountRepository
        );

        Skill skill = new Skill(10L, "demo", "owner-1", SkillVisibility.PUBLIC);
        ReflectionTestUtils.setField(skill, "id", 99L);
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");

        when(skillQueryService.getSkillDetail("team-a", "demo", "user-1", Map.of()))
                .thenReturn(new SkillQueryService.SkillDetailDTO(
                        99L, "demo", " ", "owner-1", null, "summary", "PUBLIC", "ACTIVE",
                        0L, 0, BigDecimal.ZERO, 0, false, 10L,
                        Instant.parse("2026-03-18T08:00:00Z"), Instant.parse("2026-03-18T09:00:00Z"),
                        false, false, false, false,
                        null, null, null, null, "PUBLISHED"
                ));
        when(compatSkillLookupService.resolveVisible("team-a", "demo", "user-1", Map.of()))
                .thenReturn(new CompatSkillLookupService.CompatSkillContext(namespace, skill, Optional.empty()));

        var response = facade.getSkill("team-a--demo", "user-1", null);

        assertThat(response.latestVersion()).isNull();
    }

    @Test
    void getSkill_withBlankPublishedVersion_returnsNullLatestVersion() {
        CanonicalSlugMapper canonicalSlugMapper = new CanonicalSlugMapper();
        SkillSearchAppService skillSearchAppService = mock(SkillSearchAppService.class);
        SkillQueryService skillQueryService = mock(SkillQueryService.class);
        CompatSkillLookupService compatSkillLookupService = mock(CompatSkillLookupService.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        ClawHubRegistryFacade facade = new ClawHubRegistryFacade(
                canonicalSlugMapper,
                skillSearchAppService,
                skillQueryService,
                compatSkillLookupService,
                userAccountRepository
        );

        Skill skill = new Skill(10L, "demo", "owner-1", SkillVisibility.PUBLIC);
        ReflectionTestUtils.setField(skill, "id", 99L);
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        SkillLifecycleProjectionService.VersionProjection publishedProjection =
                new SkillLifecycleProjectionService.VersionProjection(77L, "", "PUBLISHED");

        when(skillQueryService.getSkillDetail("team-a", "demo", "user-1", Map.of()))
                .thenReturn(new SkillQueryService.SkillDetailDTO(
                        99L, "demo", " ", "owner-1", null, "summary", "PUBLIC", "ACTIVE",
                        0L, 0, BigDecimal.ZERO, 0, false, 10L,
                        Instant.parse("2026-03-18T08:00:00Z"), Instant.parse("2026-03-18T09:00:00Z"),
                        false, false, false, false,
                        publishedProjection, publishedProjection, null, null, "PUBLISHED"
                ));
        when(compatSkillLookupService.resolveVisible("team-a", "demo", "user-1", Map.of()))
                .thenReturn(new CompatSkillLookupService.CompatSkillContext(namespace, skill, Optional.empty()));

        var response = facade.getSkill("team-a--demo", "user-1", null);

        assertThat(response.latestVersion()).isNull();
    }

    @Test
    void getSkill_withFoundVersionHavingPublishedAt_returnsVersionWithPublishedAt() {
        CanonicalSlugMapper canonicalSlugMapper = new CanonicalSlugMapper();
        SkillSearchAppService skillSearchAppService = mock(SkillSearchAppService.class);
        SkillQueryService skillQueryService = mock(SkillQueryService.class);
        CompatSkillLookupService compatSkillLookupService = mock(CompatSkillLookupService.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        ClawHubRegistryFacade facade = new ClawHubRegistryFacade(
                canonicalSlugMapper,
                skillSearchAppService,
                skillQueryService,
                compatSkillLookupService,
                userAccountRepository
        );

        Skill skill = new Skill(10L, "demo", "owner-1", SkillVisibility.PUBLIC);
        ReflectionTestUtils.setField(skill, "id", 99L);
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        SkillLifecycleProjectionService.VersionProjection publishedProjection =
                new SkillLifecycleProjectionService.VersionProjection(77L, "1.0.0", "PUBLISHED");
        SkillVersion versionEntity = new SkillVersion(99L, "1.0.0", "owner-1");
        versionEntity.setChangelog(null);
        ReflectionTestUtils.setField(versionEntity, "publishedAt", Instant.parse("2026-03-20T10:00:00Z"));

        when(skillQueryService.getSkillDetail("team-a", "demo", "user-1", Map.of()))
                .thenReturn(new SkillQueryService.SkillDetailDTO(
                        99L, "demo", " ", "owner-1", null, "summary", "PUBLIC", "ACTIVE",
                        0L, 0, BigDecimal.ZERO, 0, false, 10L,
                        Instant.parse("2026-03-18T08:00:00Z"), Instant.parse("2026-03-18T09:00:00Z"),
                        false, false, false, false,
                        publishedProjection, publishedProjection, null, null, "PUBLISHED"
                ));
        when(compatSkillLookupService.resolveVisible("team-a", "demo", "user-1", Map.of()))
                .thenReturn(new CompatSkillLookupService.CompatSkillContext(namespace, skill, Optional.empty()));
        when(compatSkillLookupService.findVersion(99L, "1.0.0")).thenReturn(Optional.of(versionEntity));

        var response = facade.getSkill("team-a--demo", "user-1", null);

        assertThat(response.latestVersion().version()).isEqualTo("1.0.0");
        assertThat(response.latestVersion().createdAt()).isEqualTo(Instant.parse("2026-03-20T10:00:00Z").toEpochMilli());
        assertThat(response.latestVersion().changelog()).isEmpty();
    }

    @Test
    void getSkill_withNullOwnerId_returnsEmptyOwner() {
        CanonicalSlugMapper canonicalSlugMapper = new CanonicalSlugMapper();
        SkillSearchAppService skillSearchAppService = mock(SkillSearchAppService.class);
        SkillQueryService skillQueryService = mock(SkillQueryService.class);
        CompatSkillLookupService compatSkillLookupService = mock(CompatSkillLookupService.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        ClawHubRegistryFacade facade = new ClawHubRegistryFacade(
                canonicalSlugMapper,
                skillSearchAppService,
                skillQueryService,
                compatSkillLookupService,
                userAccountRepository
        );

        Skill skill = new Skill(10L, "demo", null, SkillVisibility.PUBLIC);
        ReflectionTestUtils.setField(skill, "id", 99L);
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        SkillLifecycleProjectionService.VersionProjection publishedProjection =
                new SkillLifecycleProjectionService.VersionProjection(77L, "1.0.0", "PUBLISHED");

        when(skillQueryService.getSkillDetail("team-a", "demo", "user-1", Map.of()))
                .thenReturn(new SkillQueryService.SkillDetailDTO(
                        99L, "demo", " ", null, null, "summary", "PUBLIC", "ACTIVE",
                        0L, 0, BigDecimal.ZERO, 0, false, 10L,
                        Instant.parse("2026-03-18T08:00:00Z"), Instant.parse("2026-03-18T09:00:00Z"),
                        false, false, false, false,
                        publishedProjection, publishedProjection, null, null, "PUBLISHED"
                ));
        when(compatSkillLookupService.resolveVisible("team-a", "demo", "user-1", Map.of()))
                .thenReturn(new CompatSkillLookupService.CompatSkillContext(namespace, skill, Optional.empty()));
        when(compatSkillLookupService.findVersion(99L, "1.0.0")).thenReturn(Optional.empty());

        var response = facade.getSkill("team-a--demo", "user-1", null);

        assertThat(response.owner().displayName()).isNull();
    }

    @Test
    void getSkill_withBlankOwnerId_returnsEmptyOwner() {
        CanonicalSlugMapper canonicalSlugMapper = new CanonicalSlugMapper();
        SkillSearchAppService skillSearchAppService = mock(SkillSearchAppService.class);
        SkillQueryService skillQueryService = mock(SkillQueryService.class);
        CompatSkillLookupService compatSkillLookupService = mock(CompatSkillLookupService.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        ClawHubRegistryFacade facade = new ClawHubRegistryFacade(
                canonicalSlugMapper,
                skillSearchAppService,
                skillQueryService,
                compatSkillLookupService,
                userAccountRepository
        );

        Skill skill = new Skill(10L, "demo", "  ", SkillVisibility.PUBLIC);
        ReflectionTestUtils.setField(skill, "id", 99L);
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        SkillLifecycleProjectionService.VersionProjection publishedProjection =
                new SkillLifecycleProjectionService.VersionProjection(77L, "1.0.0", "PUBLISHED");

        when(skillQueryService.getSkillDetail("team-a", "demo", "user-1", Map.of()))
                .thenReturn(new SkillQueryService.SkillDetailDTO(
                        99L, "demo", " ", "  ", null, "summary", "PUBLIC", "ACTIVE",
                        0L, 0, BigDecimal.ZERO, 0, false, 10L,
                        Instant.parse("2026-03-18T08:00:00Z"), Instant.parse("2026-03-18T09:00:00Z"),
                        false, false, false, false,
                        publishedProjection, publishedProjection, null, null, "PUBLISHED"
                ));
        when(compatSkillLookupService.resolveVisible("team-a", "demo", "user-1", Map.of()))
                .thenReturn(new CompatSkillLookupService.CompatSkillContext(namespace, skill, Optional.empty()));
        when(compatSkillLookupService.findVersion(99L, "1.0.0")).thenReturn(Optional.empty());

        var response = facade.getSkill("team-a--demo", "user-1", null);

        assertThat(response.owner().displayName()).isNull();
    }

    @Test
    void getSkill_withFoundOwner_returnsOwnerInfo() {
        CanonicalSlugMapper canonicalSlugMapper = new CanonicalSlugMapper();
        SkillSearchAppService skillSearchAppService = mock(SkillSearchAppService.class);
        SkillQueryService skillQueryService = mock(SkillQueryService.class);
        CompatSkillLookupService compatSkillLookupService = mock(CompatSkillLookupService.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        ClawHubRegistryFacade facade = new ClawHubRegistryFacade(
                canonicalSlugMapper,
                skillSearchAppService,
                skillQueryService,
                compatSkillLookupService,
                userAccountRepository
        );

        Skill skill = new Skill(10L, "demo", "owner-1", SkillVisibility.PUBLIC);
        ReflectionTestUtils.setField(skill, "id", 99L);
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        SkillLifecycleProjectionService.VersionProjection publishedProjection =
                new SkillLifecycleProjectionService.VersionProjection(77L, "1.0.0", "PUBLISHED");
        com.iflytek.skillhub.domain.user.UserAccount owner = new com.iflytek.skillhub.domain.user.UserAccount("owner-1", "Owner Name", "owner@example.com", "https://example.com/avatar.png");

        when(skillQueryService.getSkillDetail("team-a", "demo", "user-1", Map.of()))
                .thenReturn(new SkillQueryService.SkillDetailDTO(
                        99L, "demo", " ", "owner-1", null, "summary", "PUBLIC", "ACTIVE",
                        0L, 0, BigDecimal.ZERO, 0, false, 10L,
                        Instant.parse("2026-03-18T08:00:00Z"), Instant.parse("2026-03-18T09:00:00Z"),
                        false, false, false, false,
                        publishedProjection, publishedProjection, null, null, "PUBLISHED"
                ));
        when(compatSkillLookupService.resolveVisible("team-a", "demo", "user-1", Map.of()))
                .thenReturn(new CompatSkillLookupService.CompatSkillContext(namespace, skill, Optional.empty()));
        when(compatSkillLookupService.findVersion(99L, "1.0.0")).thenReturn(Optional.empty());
        when(userAccountRepository.findById("owner-1")).thenReturn(Optional.of(owner));

        var response = facade.getSkill("team-a--demo", "user-1", null);

        assertThat(response.owner().displayName()).isEqualTo("Owner Name");
        assertThat(response.owner().image()).isEqualTo("https://example.com/avatar.png");
    }

    @Test
    void resolveDownloadUrl_withNonBlankVersion_returnsVersionedUrl() {
        CanonicalSlugMapper canonicalSlugMapper = new CanonicalSlugMapper();
        SkillSearchAppService skillSearchAppService = mock(SkillSearchAppService.class);
        SkillQueryService skillQueryService = mock(SkillQueryService.class);
        CompatSkillLookupService compatSkillLookupService = mock(CompatSkillLookupService.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        ClawHubRegistryFacade facade = new ClawHubRegistryFacade(
                canonicalSlugMapper,
                skillSearchAppService,
                skillQueryService,
                compatSkillLookupService,
                userAccountRepository
        );
        when(skillQueryService.resolveVersion("team-a", "demo", "1.0.0", null, null, "user-1", Map.of()))
                .thenReturn(new SkillQueryService.ResolvedVersionDTO(1L, "team-a", "demo", "1.0.0", 2L, "sha", true, "/download/1.0.0"));

        String downloadUrl = facade.resolveDownloadUrl("team-a--demo", "1.0.0", "user-1", null);

        assertThat(downloadUrl).isEqualTo("/download/1.0.0");
    }
}
