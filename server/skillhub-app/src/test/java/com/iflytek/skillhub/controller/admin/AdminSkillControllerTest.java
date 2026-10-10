package com.iflytek.skillhub.controller.admin;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.service.SkillGovernanceService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSkillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SkillGovernanceService skillGovernanceService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @Test
    void hideSkill_returnsUpdatedResponse() throws Exception {
        Skill skill = new Skill(1L, "demo", "owner", SkillVisibility.PUBLIC);
        given(skillGovernanceService.hideSkill(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.eq("admin"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("policy")))
            .willReturn(skill);

        PlatformPrincipal principal = new PlatformPrincipal("admin", "admin", "a@example.com", "", "github", Set.of("SUPER_ADMIN"));
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));

        mockMvc.perform(post("/api/v1/admin/skills/10/hide")
                .with(authentication(auth))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"policy\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.skillId").value(10))
            .andExpect(jsonPath("$.data.action").value("HIDE"));
    }

    @Test
    void yankVersion_returnsUpdatedResponse() throws Exception {
        SkillVersion version = new SkillVersion(10L, "1.0.0", "owner");
        version.setStatus(SkillVersionStatus.YANKED);
        given(skillGovernanceService.yankVersion(org.mockito.ArgumentMatchers.eq(33L), org.mockito.ArgumentMatchers.eq("admin"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("broken")))
            .willReturn(version);

        PlatformPrincipal principal = new PlatformPrincipal("admin", "admin", "a@example.com", "", "github", Set.of("SKILL_ADMIN"));
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_SKILL_ADMIN")));

        mockMvc.perform(post("/api/v1/admin/skills/versions/33/yank")
                .with(authentication(auth))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"broken\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.versionId").value(33))
            .andExpect(jsonPath("$.data.action").value("YANK"))
            .andExpect(jsonPath("$.data.status").value("YANKED"));
    }

    @Test
    void hideSkill_withUserAdminRole_returns403() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal("admin", "admin", "a@example.com", "", "github", Set.of("USER_ADMIN"));
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER_ADMIN")));

        mockMvc.perform(post("/api/v1/admin/skills/10/hide")
                .with(authentication(auth))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"policy\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void hideSkill_withSkillAdminRole_returns403() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal("admin", "admin", "a@example.com", "", "github", Set.of("SKILL_ADMIN"));
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_SKILL_ADMIN")));

        mockMvc.perform(post("/api/v1/admin/skills/10/hide")
                .with(authentication(auth))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"policy\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403));
    }
}
