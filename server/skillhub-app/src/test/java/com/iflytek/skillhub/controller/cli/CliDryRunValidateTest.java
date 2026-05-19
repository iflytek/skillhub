package com.iflytek.skillhub.controller.cli;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.dto.cli.CliDryRunResponse;
import com.iflytek.skillhub.service.cli.CliSkillAppService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CliDryRunValidateTest {
    @Autowired MockMvc mockMvc;
    @MockBean CliSkillAppService cliSkillAppService;

    private UsernamePasswordAuthenticationToken auth() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "tester", "t@example.com", "", "api_token", Set.of("USER"));
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("SCOPE_skill:publish")));
    }

    @Test
    void validatePublish_returnsValidResult() throws Exception {
        given(cliSkillAppService.validatePublish(
                eq("global"), any(), eq("user-1"), eq(SkillVisibility.PUBLIC), eq(Set.of("USER"))))
                .willReturn(new CliDryRunResponse(
                        true, List.of(), List.of(),
                        "my-skill", "1.0.0"));

        MockMultipartFile file = new MockMultipartFile("file", "skill.zip",
                "application/zip", new byte[]{0x50, 0x4B, 0x03, 0x04});

        mockMvc.perform(multipart("/api/cli/v1/skills/global/publish/validate")
                        .file(file)
                        .with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.resolvedSlug").value("my-skill"))
                .andExpect(jsonPath("$.data.resolvedVersion").value("1.0.0"));
    }

    @Test
    void validatePublish_returnsInvalidResult() throws Exception {
        given(cliSkillAppService.validatePublish(
                eq("global"), any(), eq("user-1"), eq(SkillVisibility.PUBLIC), eq(Set.of("USER"))))
                .willReturn(new CliDryRunResponse(
                        false, List.of("Missing required file: SKILL.md at root"), List.of(),
                        null, null));

        MockMultipartFile file = new MockMultipartFile("file", "skill.zip",
                "application/zip", new byte[]{0x50, 0x4B, 0x03, 0x04});

        mockMvc.perform(multipart("/api/cli/v1/skills/global/publish/validate")
                        .file(file)
                        .with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.errors[0]").value("Missing required file: SKILL.md at root"))
                .andExpect(jsonPath("$.data.resolvedSlug").doesNotExist());
    }

    @Test
    void validatePublish_acceptsCustomVisibility() throws Exception {
        given(cliSkillAppService.validatePublish(
                eq("global"), any(), eq("user-1"), eq(SkillVisibility.PRIVATE), eq(Set.of("USER"))))
                .willReturn(new CliDryRunResponse(
                        true, List.of(), List.of(), "my-skill", "1.0.0"));

        MockMultipartFile file = new MockMultipartFile("file", "skill.zip",
                "application/zip", new byte[]{0x50, 0x4B, 0x03, 0x04});

        mockMvc.perform(multipart("/api/cli/v1/skills/global/publish/validate")
                        .file(file)
                        .file(new MockMultipartFile("visibility", "", "text/plain", "PRIVATE".getBytes()))
                        .with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true));
    }

    @Test
    void validatePublish_rejectsInvalidVisibility() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "skill.zip",
                "application/zip", new byte[]{0x50, 0x4B, 0x03, 0x04});

        mockMvc.perform(multipart("/api/cli/v1/skills/global/publish/validate")
                        .file(file)
                        .file(new MockMultipartFile("visibility", "", "text/plain", "BOGUS".getBytes()))
                        .with(authentication(auth())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validatePublish_requiresAuthentication() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "skill.zip",
                "application/zip", new byte[]{0x50, 0x4B, 0x03, 0x04});

        mockMvc.perform(multipart("/api/cli/v1/skills/global/publish/validate")
                        .file(file))
                .andExpect(status().isUnauthorized());
    }
}
