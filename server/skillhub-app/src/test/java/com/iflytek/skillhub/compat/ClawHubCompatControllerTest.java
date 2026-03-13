package com.iflytek.skillhub.compat;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.service.SkillSearchAppService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClawHubCompatControllerTest {

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

    @Test
    void search_returns_200() throws Exception {
        given(skillSearchAppService.search("test", null, "relevance", 0, 20, null, Map.of()))
            .willReturn(new SkillSearchAppService.SearchResponse(List.of(), 0, 0, 20));

        mockMvc.perform(get("/api/compat/v1/search")
                        .param("q", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void resolve_returns_correct_downloadUrl() throws Exception {
        given(skillQueryService.resolveVersion(
                eq("global"),
                eq("my-skill"),
                isNull(),
                eq("latest"),
                isNull(),
                isNull(),
                eq(Map.<Long, NamespaceRole>of())))
            .willReturn(new SkillQueryService.ResolvedVersionDTO(
                1L,
                "global",
                "my-skill",
                "latest",
                1L,
                "sha256:test",
                true,
                "/api/v1/skills/global/my-skill/download"
            ));

        mockMvc.perform(get("/api/compat/v1/resolve/my-skill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canonicalSlug").value("my-skill"))
                .andExpect(jsonPath("$.version").value("latest"))
                .andExpect(jsonPath("$.downloadUrl").value("/api/v1/skills/global/my-skill/download"));
    }

    @Test
    void resolve_with_namespace_returns_correct_downloadUrl() throws Exception {
        given(skillQueryService.resolveVersion(
                eq("team-ai"),
                eq("my-skill"),
                isNull(),
                eq("latest"),
                isNull(),
                isNull(),
                eq(Map.<Long, NamespaceRole>of())))
            .willReturn(new SkillQueryService.ResolvedVersionDTO(
                1L,
                "team-ai",
                "my-skill",
                "latest",
                1L,
                "sha256:test",
                true,
                "/api/v1/skills/team-ai/my-skill/download"
            ));

        mockMvc.perform(get("/api/compat/v1/resolve/team-ai--my-skill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canonicalSlug").value("team-ai--my-skill"))
                .andExpect(jsonPath("$.version").value("latest"))
                .andExpect(jsonPath("$.downloadUrl").value("/api/v1/skills/team-ai/my-skill/download"));
    }

    @Test
    void resolve_with_version_returns_specified_version() throws Exception {
        given(skillQueryService.resolveVersion(
                eq("global"),
                eq("my-skill"),
                eq("1.0.0"),
                isNull(),
                isNull(),
                isNull(),
                eq(Map.<Long, NamespaceRole>of())))
            .willReturn(new SkillQueryService.ResolvedVersionDTO(
                1L,
                "global",
                "my-skill",
                "1.0.0",
                2L,
                "sha256:test",
                true,
                "/api/v1/skills/global/my-skill/download"
            ));

        mockMvc.perform(get("/api/compat/v1/resolve/my-skill")
                        .param("version", "1.0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canonicalSlug").value("my-skill"))
                .andExpect(jsonPath("$.version").value("1.0.0"))
                .andExpect(jsonPath("$.downloadUrl").value("/api/v1/skills/global/my-skill/download"));
    }

    @Test
    void whoami_with_auth_returns_user_info() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-42",
                "tester",
                "tester@example.com",
                "https://example.com/avatar.png",
                "github",
                Set.of("SUPER_ADMIN")
        );
        var auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );

        mockMvc.perform(get("/api/compat/v1/whoami")
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-42"))
                .andExpect(jsonPath("$.displayName").value("tester"))
                .andExpect(jsonPath("$.email").value("tester@example.com"));
    }
}
