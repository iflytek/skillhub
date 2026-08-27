package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.dto.SkillLabelDto;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.service.SkillLabelProjectionService;
import com.iflytek.skillhub.service.SkillSearchAppService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SkillSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private SkillSearchAppService skillSearchAppService;

    @MockBean
    private SkillLabelProjectionService skillLabelProjectionService;

    @Test
    void searchShouldUseUnifiedEnvelopeAndItemsField() throws Exception {
        when(skillSearchAppService.search(
                eq("review"),
                eq("global"),
                eq("newest"),
                eq(0),
                eq(20),
                eq(null),
                any(),
                any()))
                .thenReturn(new SkillSearchAppService.SearchResponse(List.of(), 0, 0, 20));

        mockMvc.perform(get("/api/web/skills")
                        .param("q", "review")
                        .param("namespace", "global"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void searchShouldPassExplicitSortPageAndSize() throws Exception {
        when(skillSearchAppService.search(
                eq(null),
                eq(null),
                eq("newest"),
                eq(0),
                eq(12),
                eq(null),
                any(),
                any()))
                .thenReturn(new SkillSearchAppService.SearchResponse(List.of(), 0, 0, 12));

        mockMvc.perform(get("/api/web/skills")
                        .param("sort", "newest")
                        .param("page", "0")
                        .param("size", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(12))
                .andExpect(jsonPath("$.data.page").value(0));
    }

    @Test
    void searchShouldPassLabelFilters() throws Exception {
        when(skillSearchAppService.search(
                eq("review"),
                eq(null),
                eq("newest"),
                eq(0),
                eq(20),
                eq(List.of("code-generation", "official")),
                any(),
                any()))
                .thenReturn(new SkillSearchAppService.SearchResponse(List.of(), 0, 0, 20));

        mockMvc.perform(get("/api/web/skills")
                        .param("q", "review")
                        .param("label", "code-generation")
                        .param("label", "official"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void searchShouldFallbackToDefaultsForBlankQueryParams() throws Exception {
        when(skillSearchAppService.search(
                eq(null),
                eq(null),
                eq("newest"),
                eq(0),
                eq(20),
                eq(null),
                any(),
                any()))
                .thenReturn(new SkillSearchAppService.SearchResponse(List.of(), 0, 0, 20));

        mockMvc.perform(get("/api/web/skills")
                        .param("sort", " ")
                        .param("page", "")
                        .param("size", " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    void searchShouldFallbackToDefaultsForInvalidPagination() throws Exception {
        when(skillSearchAppService.search(
                eq(null),
                eq(null),
                eq("newest"),
                eq(0),
                eq(20),
                eq(null),
                any(),
                any()))
                .thenReturn(new SkillSearchAppService.SearchResponse(List.of(), 0, 0, 20));

        mockMvc.perform(get("/api/web/skills")
                        .param("page", "NaN")
                        .param("size", "-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    void searchShouldOmitLabelsUnlessRequested() throws Exception {
        when(skillSearchAppService.search(
                eq(null), eq(null), eq("newest"), eq(0), eq(20), eq(null), any(), any()))
                .thenReturn(new SkillSearchAppService.SearchResponse(List.of(summary(7L)), 1, 0, 20));

        mockMvc.perform(get("/api/web/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].slug").value("demo-skill"))
                .andExpect(jsonPath("$.data.items[0].labels").doesNotExist());
    }

    @Test
    void searchShouldReturnLabelsWhenRequested() throws Exception {
        when(skillSearchAppService.search(
                eq(null), eq(null), eq("newest"), eq(0), eq(20), eq(null), any(), any()))
                .thenReturn(new SkillSearchAppService.SearchResponse(List.of(summary(7L)), 1, 0, 20));
        when(skillLabelProjectionService.labelsBySkillIds(List.of(7L)))
                .thenReturn(Map.of(7L, List.of(new SkillLabelDto("automation", "TOPIC", "Automation"))));

        mockMvc.perform(get("/api/web/skills").param("include", "labels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].labels[0].slug").value("automation"))
                .andExpect(jsonPath("$.data.items[0].labels[0].type").value("TOPIC"))
                .andExpect(jsonPath("$.data.items[0].labels[0].displayName").value("Automation"));
    }

    @Test
    void searchShouldReturnEmptyLabelArrayForSkillsWithoutLabels() throws Exception {
        when(skillSearchAppService.search(
                eq(null), eq(null), eq("newest"), eq(0), eq(20), eq(null), any(), any()))
                .thenReturn(new SkillSearchAppService.SearchResponse(List.of(summary(7L)), 1, 0, 20));
        when(skillLabelProjectionService.labelsBySkillIds(List.of(7L))).thenReturn(Map.of());

        mockMvc.perform(get("/api/web/skills").param("include", "labels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].labels").isArray())
                .andExpect(jsonPath("$.data.items[0].labels").isEmpty());
    }

    @Test
    void searchShouldRejectUnsupportedIncludeOptions() throws Exception {
        mockMvc.perform(get("/api/web/skills").param("include", "labels,stats"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(skillSearchAppService);
    }

    private static SkillSummaryResponse summary(Long id) {
        return new SkillSummaryResponse(
                id, "demo-skill", "Demo Skill", "A demo", "PUBLIC", "PUBLISHED",
                0L, 0, null, 0, "global", null, false, null, null, null, null, null);
    }
}
