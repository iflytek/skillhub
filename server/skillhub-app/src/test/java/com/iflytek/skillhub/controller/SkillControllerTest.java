package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.SkillFile;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.service.SkillLabelAppService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SkillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private SkillQueryService skillQueryService;

    @MockBean
    private SkillDownloadService skillDownloadService;

    @MockBean
    private SkillLabelAppService skillLabelAppService;

    @Test
    void getVersionDetailShouldReturnMetadataFields() throws Exception {
        when(skillQueryService.getVersionDetail(
                eq("team"),
                eq("demo"),
                eq("1.0.0"),
                eq((String) null),
                eq(Map.<Long, NamespaceRole>of())))
                .thenReturn(new SkillQueryService.SkillVersionDetailDTO(
                        10L,
                        "1.0.0",
                        "PUBLISHED",
                        "initial",
                        2,
                        128L,
                        Instant.parse("2026-03-12T12:00:00Z"),
                        "{\"name\":\"demo\"}",
                        "[{\"path\":\"SKILL.md\"}]"
                ));

        mockMvc.perform(get("/api/v1/skills/team/demo/versions/1.0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.version").value("1.0.0"))
                .andExpect(jsonPath("$.data.parsedMetadataJson").value("{\"name\":\"demo\"}"))
                .andExpect(jsonPath("$.data.manifestJson").value("[{\"path\":\"SKILL.md\"}]"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void getVersionDetailShouldRemainUtcAcrossJvmDefaultTimeZones() throws Exception {
        when(skillQueryService.getVersionDetail(
                eq("team"),
                eq("demo"),
                eq("1.0.0"),
                eq((String) null),
                eq(Map.<Long, NamespaceRole>of())))
                .thenReturn(new SkillQueryService.SkillVersionDetailDTO(
                        10L,
                        "1.0.0",
                        "PUBLISHED",
                        "initial",
                        2,
                        128L,
                        Instant.parse("2026-03-12T12:00:00Z"),
                        "{\"name\":\"demo\"}",
                        "[{\"path\":\"SKILL.md\"}]"
                ));

        TimeZone original = TimeZone.getDefault();
        try {
            for (String zoneId : List.of("Asia/Shanghai", "America/Los_Angeles")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
                mockMvc.perform(get("/api/v1/skills/team/demo/versions/1.0.0"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.publishedAt").value("2026-03-12T12:00:00Z"));
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void resolveVersionShouldReturnUnifiedEnvelope() throws Exception {
        when(skillQueryService.resolveVersion(
                eq("team"),
                eq("demo"),
                eq(null),
                eq("latest"),
                eq(null),
                eq((String) null),
                eq(Map.<Long, NamespaceRole>of())))
                .thenReturn(new SkillQueryService.ResolvedVersionDTO(
                        1L,
                        "team",
                        "demo",
                        "1.2.0",
                        20L,
                        "sha256:abc",
                        null,
                        "/api/v1/skills/team/demo/versions/1.2.0/download"
                ));

        mockMvc.perform(get("/api/v1/skills/team/demo/resolve").param("tag", "latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.namespace").value("team"))
                .andExpect(jsonPath("$.data.slug").value("demo"))
                .andExpect(jsonPath("$.data.downloadUrl").value("/api/v1/skills/team/demo/versions/1.2.0/download"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void getSkillDetailShouldExposePendingPreviewFlags() throws Exception {
        when(skillQueryService.getSkillDetail(
                eq("team"),
                eq("demo"),
                eq((String) null),
                eq(Map.<Long, NamespaceRole>of())))
                .thenReturn(new SkillQueryService.SkillDetailDTO(
                        1L,
                        "demo",
                        "Demo",
                        "owner-1",
                        "Alice",
                        "Pending preview",
                        "PUBLIC",
                        "ACTIVE",
                        10L,
                        2,
                        0,
                        null,
                        0,
                        false,
                        1L,
                        Instant.parse("2026-03-15T10:00:00Z"),
                        Instant.parse("2026-03-15T10:00:00Z"),
                        true,
                        false,
                        false,
                        false,
                        new com.iflytek.skillhub.domain.skill.service.SkillLifecycleProjectionService.VersionProjection(11L, "1.1.0", "PENDING_REVIEW"),
                        null,
                        new com.iflytek.skillhub.domain.skill.service.SkillLifecycleProjectionService.VersionProjection(11L, "1.1.0", "PENDING_REVIEW"),
                        null,
                        "OWNER_PREVIEW"
                ));

        mockMvc.perform(get("/api/web/skills/team/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.ownerDisplayName").value("Alice"))
                .andExpect(jsonPath("$.data.canSubmitPromotion").value(false))
                .andExpect(jsonPath("$.data.headlineVersion.version").value("1.1.0"))
                .andExpect(jsonPath("$.data.ownerPreviewVersion.id").value(11L))
                .andExpect(jsonPath("$.data.resolutionMode").value("OWNER_PREVIEW"))
                .andExpect(jsonPath("$.data.canInteract").value(false))
                .andExpect(jsonPath("$.data.canReport").value(false));
    }

    @Test
    void getSkillDetailShouldReturnForbiddenForArchivedNamespace() throws Exception {
        when(skillQueryService.getSkillDetail(
                eq("team"),
                eq("demo"),
                eq((String) null),
                eq(Map.<Long, NamespaceRole>of())))
                .thenThrow(new DomainForbiddenException("error.namespace.archived", "team"));

        mockMvc.perform(get("/api/web/skills/team/demo"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void listFilesByTagShouldReturnUnifiedEnvelope() throws Exception {
        when(skillQueryService.listFilesByTag(
                eq("team"),
                eq("demo"),
                eq("latest"),
                eq((String) null),
                eq(Map.<Long, NamespaceRole>of())))
                .thenReturn(List.of(new SkillFile(20L, "README.md", 32L, "text/markdown", "hash", "key")));

        mockMvc.perform(get("/api/v1/skills/team/demo/tags/latest/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].filePath").value("README.md"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void listVersionsShouldExposeDownloadAvailability() throws Exception {
        SkillVersion version = new SkillVersion(1L, "1.0.0", "owner-1");
        when(skillQueryService.listVersions(
                eq("team"),
                eq("demo"),
                eq((String) null),
                eq(Map.<Long, NamespaceRole>of()),
                any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(version)));
        when(skillQueryService.isDownloadAvailable(version)).thenReturn(false);

        mockMvc.perform(get("/api/v1/skills/team/demo/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].downloadAvailable").value(false));
    }

    @Test
    void compareVersionsShouldReturnStructuredDiffEnvelope() throws Exception {
        when(skillQueryService.compareVersions(
                eq("team"),
                eq("demo"),
                eq("1.0.0"),
                eq("1.1.0"),
                eq((String) null),
                eq(Map.<Long, NamespaceRole>of())))
                .thenReturn(new SkillQueryService.SkillVersionCompareDTO(
                        "1.0.0",
                        "1.1.0",
                        new SkillQueryService.SkillVersionCompareSummaryDTO(1, 0, 1, 0, 2, 1),
                        List.of(new SkillQueryService.SkillVersionCompareFileDTO(
                                "README.md",
                                "MODIFIED",
                                10L,
                                12L,
                                false,
                                false,
                                List.of(new SkillQueryService.SkillVersionCompareHunkDTO(
                                        1,
                                        2,
                                        1,
                                        3,
                                        List.of(
                                                new SkillQueryService.SkillVersionCompareLineDTO("CONTEXT", "# Demo", 1, 1),
                                                new SkillQueryService.SkillVersionCompareLineDTO("DELETE", "old", 2, null),
                                                new SkillQueryService.SkillVersionCompareLineDTO("ADD", "new", null, 2)
                                        )))))));

        mockMvc.perform(get("/api/v1/skills/team/demo/versions/compare")
                        .param("from", "1.0.0")
                        .param("to", "1.1.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.from").value("1.0.0"))
                .andExpect(jsonPath("$.data.to").value("1.1.0"))
                .andExpect(jsonPath("$.data.summary.totalFiles").value(1))
                .andExpect(jsonPath("$.data.files[0].path").value("README.md"))
                .andExpect(jsonPath("$.data.files[0].hunks[0].lines[1].type").value("DELETE"));
    }

    @Test
    void compareVersionsShouldRejectSameVersion() throws Exception {
        when(skillQueryService.compareVersions(
                eq("team"),
                eq("demo"),
                eq("1.0.0"),
                eq("1.0.0"),
                eq((String) null),
                eq(Map.<Long, NamespaceRole>of())))
                .thenThrow(new DomainBadRequestException("error.skill.version.compare.same"));

        mockMvc.perform(get("/api/v1/skills/team/demo/versions/compare")
                        .param("from", "1.0.0")
                        .param("to", "1.0.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}
