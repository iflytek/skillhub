package com.iflytek.skillhub.compat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.support.MultipartPackageExtractor;
import com.iflytek.skillhub.controller.support.ZipPackageExtractor;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.domain.social.SkillStarService;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.service.SkillSearchAppService;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class ClawHubCompatAppServiceTest {

    @Mock
    private CanonicalSlugMapper mapper;
    @Mock
    private SkillSearchAppService skillSearchAppService;
    @Mock
    private SkillQueryService skillQueryService;
    @Mock
    private SkillPublishService skillPublishService;
    @Mock
    private ZipPackageExtractor zipPackageExtractor;
    @Mock
    private MultipartPackageExtractor multipartPackageExtractor;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private CompatSkillLookupService compatSkillLookupService;
    @Mock
    private SkillStarService skillStarService;

    private ClawHubCompatAppService appService;

    @BeforeEach
    void setUp() {
        appService = new ClawHubCompatAppService(
                mapper,
                skillSearchAppService,
                skillQueryService,
                skillPublishService,
                zipPackageExtractor,
                multipartPackageExtractor,
                auditLogService,
                compatSkillLookupService,
                skillStarService
        );
    }

    @Test
    void publishSkill_uses_namespace_from_payload_when_specified() throws IOException {
        MultipartFile[] files = new MultipartFile[] {
                new MockMultipartFile("files", "SKILL.md", "text/markdown", "# demo".getBytes())
        };
        MultipartPackageExtractor.PublishPayload payload = new MultipartPackageExtractor.PublishPayload(
                "demo-skill",
                "Demo Skill",
                "1.0.0",
                null,
                true,
                List.of("ai"),
                null,
                "team-ai"
        );
        when(multipartPackageExtractor.extract(any(MultipartFile[].class), any(String.class)))
                .thenReturn(new MultipartPackageExtractor.ExtractedPackage(payload, List.of()));
        when(skillPublishService.publishFromEntries(eq("team-ai"), anyList(), eq("user-1"), eq(SkillVisibility.PUBLIC), anySet()))
                .thenReturn(publishResult(100L, 200L));

        var response = appService.publishSkill("{}", files, principal(), "127.0.0.1", "JUnit");

        assertThat(response.ok()).isTrue();
        assertThat(response.skillId()).isEqualTo("100");
        assertThat(response.versionId()).isEqualTo("200");
        verify(skillPublishService).publishFromEntries(eq("team-ai"), anyList(), eq("user-1"), eq(SkillVisibility.PUBLIC), anySet());
    }

    @Test
    void publishSkill_falls_back_to_global_when_payload_namespace_missing() throws IOException {
        MultipartFile[] files = new MultipartFile[] {
                new MockMultipartFile("files", "SKILL.md", "text/markdown", "# demo".getBytes())
        };
        MultipartPackageExtractor.PublishPayload payload = new MultipartPackageExtractor.PublishPayload(
                "demo-skill",
                "Demo Skill",
                "1.0.0",
                null,
                true,
                List.of("ai"),
                null,
                null
        );
        when(multipartPackageExtractor.extract(any(MultipartFile[].class), any(String.class)))
                .thenReturn(new MultipartPackageExtractor.ExtractedPackage(payload, List.of()));
        when(skillPublishService.publishFromEntries(eq("global"), anyList(), eq("user-1"), eq(SkillVisibility.PUBLIC), anySet()))
                .thenReturn(publishResult(101L, 201L));

        var response = appService.publishSkill("{}", files, principal(), "127.0.0.1", "JUnit");

        assertThat(response.ok()).isTrue();
        assertThat(response.skillId()).isEqualTo("101");
        assertThat(response.versionId()).isEqualTo("201");
        verify(skillPublishService).publishFromEntries(eq("global"), anyList(), eq("user-1"), eq(SkillVisibility.PUBLIC), anySet());
    }

    @Test
    void publish_falls_back_to_global_when_namespace_query_missing() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "demo.zip", "application/zip", "zip".getBytes());
        List<PackageEntry> entries = List.of(new PackageEntry("SKILL.md", "# demo".getBytes(), 6L, "text/markdown"));
        when(zipPackageExtractor.extract(file)).thenReturn(entries);
        when(skillPublishService.publishFromEntries(eq("global"), eq(entries), eq("user-1"), eq(SkillVisibility.PUBLIC), anySet()))
                .thenReturn(publishResult(102L, 202L));

        var response = appService.publish(file, null, principal(), "127.0.0.1", "JUnit");

        assertThat(response.ok()).isTrue();
        assertThat(response.skillId()).isEqualTo("102");
        assertThat(response.versionId()).isEqualTo("202");
        verify(skillPublishService).publishFromEntries(eq("global"), eq(entries), eq("user-1"), eq(SkillVisibility.PUBLIC), anySet());
    }

    @Test
    void publish_uses_namespace_query_when_specified() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "demo.zip", "application/zip", "zip".getBytes());
        List<PackageEntry> entries = List.of(new PackageEntry("SKILL.md", "# demo".getBytes(), 6L, "text/markdown"));
        when(zipPackageExtractor.extract(file)).thenReturn(entries);
        when(skillPublishService.publishFromEntries(eq("team-ai"), eq(entries), eq("user-1"), eq(SkillVisibility.PUBLIC), anySet()))
                .thenReturn(publishResult(103L, 203L));

        var response = appService.publish(file, "team-ai", principal(), "127.0.0.1", "JUnit");

        assertThat(response.ok()).isTrue();
        assertThat(response.skillId()).isEqualTo("103");
        assertThat(response.versionId()).isEqualTo("203");
        verify(skillPublishService).publishFromEntries(eq("team-ai"), eq(entries), eq("user-1"), eq(SkillVisibility.PUBLIC), anySet());
    }

    @Test
    void publish_falls_back_to_global_when_namespace_query_blank() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "demo.zip", "application/zip", "zip".getBytes());
        List<PackageEntry> entries = List.of(new PackageEntry("SKILL.md", "# demo".getBytes(), 6L, "text/markdown"));
        when(zipPackageExtractor.extract(file)).thenReturn(entries);
        when(skillPublishService.publishFromEntries(eq("global"), eq(entries), eq("user-1"), eq(SkillVisibility.PUBLIC), anySet()))
                .thenReturn(publishResult(104L, 204L));

        var response = appService.publish(file, "   ", principal(), "127.0.0.1", "JUnit");

        assertThat(response.ok()).isTrue();
        assertThat(response.skillId()).isEqualTo("104");
        assertThat(response.versionId()).isEqualTo("204");
        verify(skillPublishService).publishFromEntries(eq("global"), eq(entries), eq("user-1"), eq(SkillVisibility.PUBLIC), anySet());
    }

    private PlatformPrincipal principal() {
        return new PlatformPrincipal(
                "user-1",
                "tester",
                "tester@example.com",
                null,
                "github",
                Set.of("SUPER_ADMIN")
        );
    }

    private SkillPublishService.PublishResult publishResult(Long skillId, Long versionId) {
        SkillVersion version = new SkillVersion(skillId, "1.0.0", "user-1");
        ReflectionTestUtils.setField(version, "id", versionId);
        return new SkillPublishService.PublishResult(skillId, "demo-skill", version);
    }
}
