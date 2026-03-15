package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.SkillFile;
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
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
                        LocalDateTime.of(2026, 3, 12, 12, 0),
                        "{\"name\":\"demo\"}",
                        "[{\"path\":\"SKILL.md\"}]"
                ));

        mockMvc.perform(get("/api/v1/skills/team/demo/versions/1.0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").isNotEmpty())
                .andExpect(jsonPath("$.data.version").value("1.0.0"))
                .andExpect(jsonPath("$.data.parsedMetadataJson").value("{\"name\":\"demo\"}"))
                .andExpect(jsonPath("$.data.manifestJson").value("[{\"path\":\"SKILL.md\"}]"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
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
                        "Pending preview",
                        "PUBLIC",
                        "ACTIVE",
                        10L,
                        2,
                        null,
                        0,
                        false,
                        "1.1.0",
                        1L,
                        LocalDateTime.of(2026, 3, 15, 10, 0),
                        LocalDateTime.of(2026, 3, 15, 10, 0),
                        null,
                        true,
                        "PENDING_REVIEW",
                        false
                ));

        mockMvc.perform(get("/api/web/skills/team/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestVersion").value("1.1.0"))
                .andExpect(jsonPath("$.data.viewingVersionStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.canInteract").value(false));
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
}
