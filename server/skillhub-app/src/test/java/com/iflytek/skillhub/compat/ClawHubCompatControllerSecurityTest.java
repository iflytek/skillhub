package com.iflytek.skillhub.compat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.compat.dto.ClawHubSkillResponse;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClawHubCompatControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @MockBean
    private ClawHubCompatAppService clawHubCompatAppService;

    @Test
    void getSkill_returnsNotFound_whenAnonymousCannotAccessPrivateSkill() throws Exception {
        when(clawHubCompatAppService.getSkill(eq("priv"), isNull(), isNull()))
                .thenThrow(new DomainNotFoundException("error.skill.notFound", "priv"));

        mockMvc.perform(get("/api/v1/skills/priv"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSkill_returnsSkill_whenCallerHasNamespacePermission() throws Exception {
        var roles = Map.of(1L, NamespaceRole.ADMIN);
        var response = new ClawHubSkillResponse(
                new ClawHubSkillResponse.SkillInfo(
                        "team-ai--priv",
                        "Private Skill",
                        "summary",
                        Map.of(),
                        Map.of(),
                        0L,
                        0L
                ),
                null,
                null,
                new ClawHubSkillResponse.ModerationInfo(false, false, "clean", new String[0], null, null, null)
        );
        when(clawHubCompatAppService.getSkill("team-ai--priv", "admin-1", roles)).thenReturn(response);

        mockMvc.perform(get("/api/v1/skills/team-ai--priv")
                        .requestAttr("userId", "admin-1")
                        .requestAttr("userNsRoles", roles))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skill.slug").value("team-ai--priv"));

        verify(clawHubCompatAppService).getSkill("team-ai--priv", "admin-1", roles);
    }

    @Test
    void downloadQuery_returnsNotFound_whenAnonymousCannotAccessPrivateLegacySlug() throws Exception {
        when(clawHubCompatAppService.downloadLocationByQuery(eq("priv"), eq("latest"), isNull(), isNull()))
                .thenThrow(new DomainNotFoundException("error.skill.notFound", "priv"));

        mockMvc.perform(get("/api/v1/download")
                        .param("slug", "priv")
                .param("version", "latest"))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadQuery_returnsNotFound_whenUserWithoutNamespaceRoleAccessesPrivateLegacySlug() throws Exception {
        when(clawHubCompatAppService.downloadLocationByQuery("priv", "latest", "user-1", Map.of()))
                .thenThrow(new DomainNotFoundException("error.skill.notFound", "priv"));

        mockMvc.perform(get("/api/v1/download")
                        .param("slug", "priv")
                        .param("version", "latest")
                        .requestAttr("userId", "user-1")
                        .requestAttr("userNsRoles", Map.of()))
                .andExpect(status().isNotFound());
    }
}
