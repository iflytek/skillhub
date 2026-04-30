package com.iflytek.skillhub.compat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.support.MultipartPackageExtractor;
import com.iflytek.skillhub.controller.support.ZipPackageExtractor;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService.PublishResult;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.social.SkillStarService;
import com.iflytek.skillhub.service.SkillSearchAppService;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    private final ClawHubCompatAppService service = new ClawHubCompatAppService(
            new CanonicalSlugMapper(),
            skillSearchAppService,
            skillQueryService,
            skillPublishService,
            zipPackageExtractor,
            multipartPackageExtractor,
            auditLogService,
            compatSkillLookupService,
            skillStarService
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
    void publishSkill_usesNamespaceDerivedFromCanonicalSlugWhenPayloadNamespaceMissing() throws Exception {
        MultipartPackageExtractor.PublishPayload payload = new MultipartPackageExtractor.PublishPayload(
                null, "team-a--demo", "Demo", "1.0.0", null, true, null, null);
        MultipartPackageExtractor.ExtractedPackage extracted = new MultipartPackageExtractor.ExtractedPackage(
                payload,
                java.util.List.of(new PackageEntry("SKILL.md", "x".getBytes(StandardCharsets.UTF_8), 1, "text/markdown"))
        );
        SkillVersion version = new SkillVersion(10L, "1.0.0", "user-1");
        org.springframework.test.util.ReflectionTestUtils.setField(version, "id", 33L);
        when(multipartPackageExtractor.extract(null, "{\"slug\":\"team-a--demo\"}")).thenReturn(extracted);
        when(skillPublishService.publishFromEntries("team-a", extracted.entries(), "user-1", SkillVisibility.PUBLIC, Set.of("PUBLISHER"), false))
                .thenReturn(new PublishResult(10L, "demo", version));

        var response = service.publishSkill(
                "{\"slug\":\"team-a--demo\"}",
                null,
                false,
                new PlatformPrincipal("user-1", "User", null, null, null, Set.of("PUBLISHER")),
                "203.0.113.10",
                "JUnit"
        );

        assertThat(response.ok()).isTrue();
        assertThat(response.skillId()).isEqualTo("10");
        assertThat(response.versionId()).isEqualTo("33");
    }

    @Test
    void starSkill_returnsAlreadyStarredFlag() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        Skill skill = new Skill(1L, "my-skill", "owner-1", SkillVisibility.PUBLIC);
        org.springframework.test.util.ReflectionTestUtils.setField(skill, "id", 42L);
        when(compatSkillLookupService.resolveVisible("team-a", "my-skill", "user-1"))
                .thenReturn(new CompatSkillLookupService.CompatSkillContext(namespace, skill, Optional.empty()));
        when(skillStarService.isStarred(42L, "user-1")).thenReturn(true);

        var response = service.starSkill(
                "team-a--my-skill",
                new PlatformPrincipal("user-1", "User", null, null, null, Set.of())
        );

        assertThat(response.ok()).isTrue();
        assertThat(response.alreadyStarred()).isTrue();
        verify(skillStarService).star(42L, "user-1");
    }

    @Test
    void unstarSkill_returnsAlreadyUnstarredWhenSkillWasNotStarred() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        Skill skill = new Skill(1L, "my-skill", "owner-1", SkillVisibility.PUBLIC);
        org.springframework.test.util.ReflectionTestUtils.setField(skill, "id", 42L);
        when(compatSkillLookupService.resolveVisible("team-a", "my-skill", "user-1"))
                .thenReturn(new CompatSkillLookupService.CompatSkillContext(namespace, skill, Optional.empty()));
        when(skillStarService.isStarred(42L, "user-1")).thenReturn(false);

        var response = service.unstarSkill(
                "team-a--my-skill",
                new PlatformPrincipal("user-1", "User", null, null, null, Set.of())
        );

        assertThat(response.ok()).isTrue();
        assertThat(response.alreadyUnstarred()).isTrue();
        verify(skillStarService).unstar(42L, "user-1");
    }

    @Test
    void whoami_mapsPrincipalFields() {
        var response = service.whoami(new PlatformPrincipal("user-1", "User", null, "avatar.png", null, Set.of()));

        assertThat(response.user().handle()).isEqualTo("user-1");
        assertThat(response.user().displayName()).isEqualTo("User");
        assertThat(response.user().image()).isEqualTo("avatar.png");
    }
}
