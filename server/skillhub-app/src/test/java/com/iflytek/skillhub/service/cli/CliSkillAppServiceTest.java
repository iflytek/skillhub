package com.iflytek.skillhub.service.cli;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.dto.SkillLifecycleVersionResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.dto.cli.CliDeleteResponse;
import com.iflytek.skillhub.dto.cli.CliPublishResponse;
import com.iflytek.skillhub.dto.cli.CliResolveResponse;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.SkillDeleteAppService;
import com.iflytek.skillhub.service.SkillSearchAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CliSkillAppServiceTest {

    @Mock SkillSearchAppService skillSearchAppService;
    @Mock SkillQueryService skillQueryService;
    @Mock SkillDownloadService skillDownloadService;
    @Mock SkillDeleteAppService skillDeleteAppService;
    @Mock SkillPublishService skillPublishService;

    private CliSkillAppService service;

    @BeforeEach
    void setUp() {
        service = new CliSkillAppService(
                skillSearchAppService, skillQueryService,
                skillDownloadService, skillDeleteAppService, skillPublishService);
    }

    @Test
    void search_mapsResultsToCliFormat() {
        var searchResponse = new SkillSearchAppService.SearchResponse(
                List.of(new SkillSummaryResponse(
                        1L, "pdf-parser", "PDF Parser", "Parse PDFs",
                        "PUBLIC", "ACTIVE", 100L, 5, BigDecimal.valueOf(4.5), 10,
                        "global", Instant.now(), false,
                        new SkillLifecycleVersionResponse(1L, "1.2.0", "PUBLISHED"),
                        new SkillLifecycleVersionResponse(1L, "1.2.0", "PUBLISHED"),
                        null, "PUBLISHED"
                )),
                1L, 0, 20
        );
        given(skillSearchAppService.search("pdf", null, "newest", 0, 20, null, null))
                .willReturn(searchResponse);

        var result = service.search("pdf", 20, null, null);

        assertEquals(1, result.items().size());
        assertEquals("global", result.items().get(0).namespace());
        assertEquals("pdf-parser", result.items().get(0).slug());
        assertEquals("1.2.0", result.items().get(0).latestVersion());
        assertEquals("Parse PDFs", result.items().get(0).summary());
        assertEquals(1L, result.total());
        assertEquals(20, result.limit());
    }

    @Test
    void resolve_delegatesToQueryService() {
        given(skillQueryService.resolveVersion("global", "demo", "2.0.0", null, null, "user-1", Map.of()))
                .willReturn(new SkillQueryService.ResolvedVersionDTO(
                        10L, "global", "demo", "2.0.0", 42L, "abc123", null,
                        "/api/v1/skills/global/demo/versions/2.0.0/download"
                ));

        CliResolveResponse response = service.resolve("global", "demo", "2.0.0", "user-1", Map.of());

        assertEquals("global", response.namespace());
        assertEquals("demo", response.slug());
        assertEquals("2.0.0", response.version());
        assertEquals(42L, response.versionId());
        assertEquals("abc123", response.fingerprint());
    }

    @Test
    void deleteRemote_delegatesToDeleteAppService() {
        var auditContext = new AuditRequestContext("127.0.0.1", "CLI/1.0");
        given(skillDeleteAppService.deleteSkill("global", "demo", null, "user-1", auditContext))
                .willReturn(new SkillDeleteAppService.DeleteResult(10L, "global", "demo", true));

        CliDeleteResponse response = service.deleteRemote("global", "demo", "user-1", auditContext);

        assertTrue(response.ok());
        assertEquals("remote", response.scope());
        assertEquals("delete", response.action());
        assertEquals("global", response.namespace());
        assertEquals("demo", response.slug());
    }

    @Test
    void publish_delegatesToPublishService() {
        List<PackageEntry> entries = List.of(
                new PackageEntry("skillhub.yaml", "name: test".getBytes(), 10, "application/x-yaml")
        );
        var mockVersion = org.mockito.Mockito.mock(SkillVersion.class);
        given(mockVersion.getVersion()).willReturn("1.0.0");
        given(skillPublishService.publishFromEntries("global", entries, "user-1", SkillVisibility.PUBLIC, Set.of("USER"), false))
                .willReturn(new SkillPublishService.PublishResult(1L, "test-skill", mockVersion));

        CliPublishResponse response = service.publish("global", entries, "user-1", SkillVisibility.PUBLIC, Set.of("USER"));

        assertEquals("global", response.namespace());
        assertEquals("test-skill", response.slug());
        assertEquals("1.0.0", response.version());
        assertEquals("PUBLIC", response.visibility());
    }
}
