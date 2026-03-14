package com.iflytek.skillhub.compat;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.service.SkillSearchAppService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClawHubRegistryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @MockBean
    private SkillSearchAppService skillSearchAppService;

    @MockBean
    private SkillQueryService skillQueryService;

    @MockBean
    private SkillRepository skillRepository;

    @MockBean
    private SkillVersionRepository skillVersionRepository;

    @MockBean
    private UserAccountRepository userAccountRepository;

    @Test
    void search_returns_clawhub_registry_schema() throws Exception {
        LocalDateTime updatedAt = LocalDateTime.of(2026, 3, 13, 10, 30);
        given(skillSearchAppService.search("test", null, "relevance", 0, 2, null, Map.of()))
                .willReturn(new SkillSearchAppService.SearchResponse(
                        List.of(
                                new SkillSummaryResponse(
                                        1L,
                                        "global-skill",
                                        "Global Skill",
                                        "global summary",
                                        10L,
                                        5,
                                        BigDecimal.ZERO,
                                        0,
                                        "1.2.0",
                                        "global",
                                        updatedAt
                                ),
                                new SkillSummaryResponse(
                                        2L,
                                        "team-skill",
                                        "Team Skill",
                                        "team summary",
                                        20L,
                                        8,
                                        BigDecimal.ONE,
                                        2,
                                        "2.0.0",
                                        "team-ai",
                                        updatedAt.plusHours(1)
                                )
                        ),
                        2,
                        0,
                        2
                ));

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "test")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.results[0].slug").value("global-skill"))
                .andExpect(jsonPath("$.results[0].displayName").value("Global Skill"))
                .andExpect(jsonPath("$.results[0].version").value("1.2.0"))
                .andExpect(jsonPath("$.results[0].score").value(1.0))
                .andExpect(jsonPath("$.results[0].updatedAt").value(toEpochMillis(updatedAt)))
                .andExpect(jsonPath("$.results[1].slug").value("team-ai--team-skill"))
                .andExpect(jsonPath("$.results[1].displayName").value("Team Skill"))
                .andExpect(jsonPath("$.results[1].version").value("2.0.0"))
                .andExpect(jsonPath("$.results[1].score").value(0.999))
                .andExpect(jsonPath("$.results[1].updatedAt").value(toEpochMillis(updatedAt.plusHours(1))));
    }

    @Test
    void get_skill_returns_clawhub_install_metadata() throws Exception {
        LocalDateTime skillCreatedAt = LocalDateTime.of(2026, 1, 1, 9, 0);
        LocalDateTime skillUpdatedAt = LocalDateTime.of(2026, 3, 10, 18, 30);
        LocalDateTime versionPublishedAt = LocalDateTime.of(2026, 3, 12, 12, 0);

        given(skillQueryService.getSkillDetail(
                eq("global"),
                eq("global-skill"),
                isNull(),
                eq(Map.<Long, NamespaceRole>of())))
                .willReturn(new SkillQueryService.SkillDetailDTO(
                        1L,
                        "global-skill",
                        "Global Skill",
                        "global summary",
                        "PUBLIC",
                        "ACTIVE",
                        10L,
                        5,
                        BigDecimal.ZERO,
                        0,
                        false,
                        "1.2.0",
                        1L
                ));

        Skill skill = new Skill(1L, "global-skill", "owner-1", SkillVisibility.PUBLIC);
        skill.setDisplayName("Global Skill");
        skill.setSummary("global summary");
        ReflectionTestUtils.setField(skill, "id", 1L);
        ReflectionTestUtils.setField(skill, "createdAt", skillCreatedAt);
        ReflectionTestUtils.setField(skill, "updatedAt", skillUpdatedAt);

        SkillVersion version = new SkillVersion(1L, "1.2.0", "owner-1");
        version.setChangelog("Initial release");
        version.setPublishedAt(versionPublishedAt);
        ReflectionTestUtils.setField(version, "id", 11L);
        ReflectionTestUtils.setField(version, "createdAt", versionPublishedAt.minusHours(2));

        given(skillRepository.findById(1L)).willReturn(java.util.Optional.of(skill));
        given(skillVersionRepository.findBySkillIdAndVersion(1L, "1.2.0")).willReturn(java.util.Optional.of(version));
        given(userAccountRepository.findById("owner-1"))
                .willReturn(java.util.Optional.of(new UserAccount(
                        "owner-1",
                        "Skill Owner",
                        "owner@example.com",
                        "https://example.com/avatar.png"
                )));

        mockMvc.perform(get("/api/v1/skills/global-skill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skill.slug").value("global-skill"))
                .andExpect(jsonPath("$.skill.displayName").value("Global Skill"))
                .andExpect(jsonPath("$.skill.summary").value("global summary"))
                .andExpect(jsonPath("$.skill.createdAt").value(toEpochMillis(skillCreatedAt)))
                .andExpect(jsonPath("$.skill.updatedAt").value(toEpochMillis(skillUpdatedAt)))
                .andExpect(jsonPath("$.latestVersion.version").value("1.2.0"))
                .andExpect(jsonPath("$.latestVersion.createdAt").value(toEpochMillis(versionPublishedAt)))
                .andExpect(jsonPath("$.latestVersion.changelog").value("Initial release"))
                .andExpect(jsonPath("$.owner.displayName").value("Skill Owner"))
                .andExpect(jsonPath("$.owner.image").value("https://example.com/avatar.png"))
                .andExpect(jsonPath("$.moderation.isSuspicious").value(false))
                .andExpect(jsonPath("$.moderation.isMalwareBlocked").value(false))
                .andExpect(jsonPath("$.moderation.verdict").value("clean"));
    }

    @Test
    void download_redirects_to_versioned_skill_url() throws Exception {
        given(skillQueryService.resolveVersion(
                eq("team-ai"),
                eq("team-skill"),
                eq("2.0.0"),
                isNull(),
                isNull(),
                isNull(),
                eq(Map.<Long, NamespaceRole>of())))
                .willReturn(new SkillQueryService.ResolvedVersionDTO(
                        2L,
                        "team-ai",
                        "team-skill",
                        "2.0.0",
                        21L,
                        "sha256:test",
                        true,
                        "/api/v1/skills/team-ai/team-skill/versions/2.0.0/download"
                ));

        mockMvc.perform(get("/api/v1/download")
                        .param("slug", "team-ai--team-skill")
                        .param("version", "2.0.0"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/api/v1/skills/team-ai/team-skill/versions/2.0.0/download"));
    }

    private long toEpochMillis(LocalDateTime timestamp) {
        return timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
