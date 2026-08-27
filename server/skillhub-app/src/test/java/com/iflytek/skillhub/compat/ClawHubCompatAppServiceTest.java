package com.iflytek.skillhub.compat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.controller.support.MultipartPackageExtractor;
import com.iflytek.skillhub.controller.support.ZipPackageExtractor;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.domain.social.SkillStarService;
import com.iflytek.skillhub.compat.dto.ClawHubSkillListResponse;
import com.iflytek.skillhub.dto.SkillLabelDto;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.service.SkillLabelProjectionService;
import com.iflytek.skillhub.service.SkillSearchAppService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ClawHubCompatAppServiceTest {

    private final SkillSearchAppService skillSearchAppService = mock(SkillSearchAppService.class);
    private final SkillQueryService skillQueryService = mock(SkillQueryService.class);
    private final SkillPublishService skillPublishService = mock(SkillPublishService.class);
    private final ZipPackageExtractor zipPackageExtractor = mock(ZipPackageExtractor.class);
    private final MultipartPackageExtractor multipartPackageExtractor = mock(MultipartPackageExtractor.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final CompatSkillLookupService compatSkillLookupService = mock(CompatSkillLookupService.class);
    private final SkillStarService skillStarService = mock(SkillStarService.class);
    private final SkillLabelProjectionService skillLabelProjectionService = mock(SkillLabelProjectionService.class);

    private final ClawHubCompatAppService service = new ClawHubCompatAppService(
            new CanonicalSlugMapper(),
            skillSearchAppService,
            skillQueryService,
            skillPublishService,
            zipPackageExtractor,
            multipartPackageExtractor,
            auditLogService,
            compatSkillLookupService,
            skillStarService,
            new RequestIdAccessor(),
            skillLabelProjectionService
    );

    @Test
    void downloadLocationByQuery_throwsNotFound_whenLegacySkillIsPrivateForAnonymousCaller() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        Skill privateSkill = new Skill(1L, "priv", "owner-1", SkillVisibility.PRIVATE);
        CompatSkillLookupService.CompatSkillContext context = new CompatSkillLookupService.CompatSkillContext(
                namespace,
                privateSkill,
                Optional.empty()
        );

        when(compatSkillLookupService.findByLegacySlug("priv")).thenReturn(context);
        when(compatSkillLookupService.canAccess(privateSkill, null, Map.of())).thenReturn(false);

        assertThatThrownBy(() -> service.downloadLocationByQuery("priv", "latest", null, null))
                .isInstanceOf(DomainNotFoundException.class);
    }

    @Test
    void downloadLocationByQuery_returnsCanonicalPath_whenLegacySkillIsVisible() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        Skill publicSkill = new Skill(1L, "my-skill", "owner-1", SkillVisibility.PUBLIC);
        CompatSkillLookupService.CompatSkillContext context = new CompatSkillLookupService.CompatSkillContext(
                namespace,
                publicSkill,
                Optional.empty()
        );

        when(compatSkillLookupService.findByLegacySlug("my-skill")).thenReturn(context);
        when(compatSkillLookupService.canAccess(publicSkill, null, Map.of())).thenReturn(true);

        String location = service.downloadLocationByQuery("my-skill", "latest", null, null);

        assertThat(location).isEqualTo("/api/v1/skills/team-a/my-skill/download");
    }

    @Test
    void downloadLocationByQuery_percentEncodesNonAsciiSlug() {
        // A non-ASCII slug (e.g. a Chinese skill name) must be percent-encoded, otherwise
        // Tomcat drops the Location header (ISO-8859-1 only) and the ClawHub CLI download fails.
        Namespace namespace = new Namespace("global", "Global", "owner-1");
        Skill cjkSkill = new Skill(1L, "需求", "owner-1", SkillVisibility.PUBLIC);
        CompatSkillLookupService.CompatSkillContext context = new CompatSkillLookupService.CompatSkillContext(
                namespace,
                cjkSkill,
                Optional.empty()
        );

        when(compatSkillLookupService.findByLegacySlug("需求")).thenReturn(context);
        when(compatSkillLookupService.canAccess(cjkSkill, null, Map.of())).thenReturn(true);

        String location = service.downloadLocationByQuery("需求", "20260707.025847", null, null);

        assertThat(location)
                .isEqualTo("/api/v1/skills/global/%E9%9C%80%E6%B1%82/versions/20260707.025847/download");
        // The header value must be writable as ISO-8859-1 (all bytes in 0-255).
        assertThat(java.nio.charset.StandardCharsets.ISO_8859_1.newEncoder().canEncode(location)).isTrue();
    }

    @Test
    void downloadLocationByQuery_includesVersionSegmentForAsciiSlug() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        Skill publicSkill = new Skill(1L, "my-skill", "owner-1", SkillVisibility.PUBLIC);
        CompatSkillLookupService.CompatSkillContext context = new CompatSkillLookupService.CompatSkillContext(
                namespace,
                publicSkill,
                Optional.empty()
        );

        when(compatSkillLookupService.findByLegacySlug("my-skill")).thenReturn(context);
        when(compatSkillLookupService.canAccess(publicSkill, null, Map.of())).thenReturn(true);

        String location = service.downloadLocationByQuery("my-skill", "20260707.025847", null, null);

        assertThat(location).isEqualTo("/api/v1/skills/team-a/my-skill/versions/20260707.025847/download");
    }

    @Test
    void listSkills_omitsLabelsByDefault() {
        when(skillSearchAppService.search("", null, "newest", 0, 25, null, Map.of()))
                .thenReturn(new SkillSearchAppService.SearchResponse(List.of(summary(7L)), 1, 0, 25));

        ClawHubSkillListResponse response = service.listSkills(0, 25, null, null, Map.of());

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).labels()).isNull();
    }

    @Test
    void listSkills_returnsLabelsWhenRequested() {
        when(skillSearchAppService.search("", null, "newest", 0, 25, null, Map.of()))
                .thenReturn(new SkillSearchAppService.SearchResponse(List.of(summary(7L)), 1, 0, 25));
        when(skillLabelProjectionService.labelsBySkillIds(List.of(7L)))
                .thenReturn(Map.of(7L, List.of(new SkillLabelDto("automation", "RECOMMENDED", "Automation"))));

        ClawHubSkillListResponse response = service.listSkills(0, 25, null, true, null, Map.of());

        assertThat(response.items().get(0).labels())
                .extracting(SkillLabelDto::slug)
                .containsExactly("automation");
    }

    private static SkillSummaryResponse summary(Long id) {
        return new SkillSummaryResponse(
                id, "demo-skill", "Demo Skill", "A demo", "PUBLIC", "PUBLISHED",
                0L, 0, null, 0, "global", null, false, null, null, null, null, null);
    }
}
