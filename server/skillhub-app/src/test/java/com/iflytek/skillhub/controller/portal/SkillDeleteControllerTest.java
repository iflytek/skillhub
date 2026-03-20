package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.service.SkillDeleteAppService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class SkillDeleteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SkillDeleteAppService skillDeleteAppService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @Test
    void deleteSkill_allowsSuperAdminAndReturnsDeletedResponse() throws Exception {
        given(skillDeleteAppService.deleteSkill(
                org.mockito.ArgumentMatchers.eq("global"),
                org.mockito.ArgumentMatchers.eq("demo-skill"),
                org.mockito.ArgumentMatchers.eq("super-1"),
                org.mockito.ArgumentMatchers.any()))
                .willReturn(new SkillDeleteAppService.DeleteResult(11L, "global", "demo-skill", true));

        PlatformPrincipal principal = new PlatformPrincipal(
                "super-1", "Super", "super@example.com", "", "api_token", Set.of("SUPER_ADMIN"));
        var auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(
                        new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"),
                        new SimpleGrantedAuthority("SCOPE_skill:delete")
                ));

        mockMvc.perform(delete("/api/v1/skills/global/demo-skill")
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andExpect(jsonPath("$.data.skillId").value(11))
                .andExpect(jsonPath("$.data.namespace").value("global"))
                .andExpect(jsonPath("$.data.slug").value("demo-skill"));
    }

    @Test
    void deleteSkill_rejectsNonSuperAdmin() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "skill-1", "Skill Admin", "skill@example.com", "", "api_token", Set.of("SKILL_ADMIN"));
        var auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(
                        new SimpleGrantedAuthority("ROLE_SKILL_ADMIN"),
                        new SimpleGrantedAuthority("SCOPE_skill:delete")
                ));

        mockMvc.perform(delete("/api/v1/skills/global/demo-skill")
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }
}
