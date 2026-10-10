package com.iflytek.skillhub.controller.cli;

import com.iflytek.skillhub.auth.entity.ApiToken;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.cli.CliDryRunResponse;
import com.iflytek.skillhub.service.cli.CliSkillAppService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CliDryRunValidateTest {
    @Autowired MockMvc mockMvc;
    @MockBean CliSkillAppService cliSkillAppService;
    @MockBean ApiTokenService apiTokenService;
    @MockBean UserAccountRepository userAccountRepository;
    @MockBean UserRoleBindingRepository userRoleBindingRepository;

    private void givenValidPublishToken() {
        ApiToken token = new ApiToken("user-1", "cli", "sk_test", "hash", "[\"skill:publish\"]");
        UserAccount user = new UserAccount("user-1", "tester", "t@example.com", "");

        given(apiTokenService.validateToken("test-token")).willReturn(Optional.of(token));
        given(userAccountRepository.findById("user-1")).willReturn(Optional.of(user));
        given(userRoleBindingRepository.findByUserId("user-1")).willReturn(List.of());
    }

    @Test
    void validatePublish_returnsValidResult() throws Exception {
        givenValidPublishToken();
        given(cliSkillAppService.validatePublish(
                eq("global"), any(), eq("user-1"), eq(SkillVisibility.PUBLIC), eq(Set.of("USER"))))
                .willReturn(new CliDryRunResponse(
                        true, List.of(), List.of(),
                        "my-skill", "1.0.0"));

        MockMultipartFile file = new MockMultipartFile("file", "skill.zip",
                "application/zip", new byte[]{0x50, 0x4B, 0x03, 0x04});

        mockMvc.perform(multipart("/api/cli/v1/skills/global/publish/validate")
                        .file(file)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.resolvedSlug").value("my-skill"))
                .andExpect(jsonPath("$.data.resolvedVersion").value("1.0.0"));
    }

    @Test
    void validatePublish_returnsInvalidResult() throws Exception {
        givenValidPublishToken();
        given(cliSkillAppService.validatePublish(
                eq("global"), any(), eq("user-1"), eq(SkillVisibility.PUBLIC), eq(Set.of("USER"))))
                .willReturn(new CliDryRunResponse(
                        false, List.of("Missing required file: SKILL.md at root"), List.of(),
                        null, null));

        MockMultipartFile file = new MockMultipartFile("file", "skill.zip",
                "application/zip", new byte[]{0x50, 0x4B, 0x03, 0x04});

        mockMvc.perform(multipart("/api/cli/v1/skills/global/publish/validate")
                        .file(file)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.errors[0]").value("Missing required file: SKILL.md at root"))
                .andExpect(jsonPath("$.data.resolvedSlug").doesNotExist());
    }

    @Test
    void validatePublish_acceptsCustomVisibility() throws Exception {
        givenValidPublishToken();
        given(cliSkillAppService.validatePublish(
                eq("global"), any(), eq("user-1"), eq(SkillVisibility.PRIVATE), eq(Set.of("USER"))))
                .willReturn(new CliDryRunResponse(
                        true, List.of(), List.of(), "my-skill", "1.0.0"));

        MockMultipartFile file = new MockMultipartFile("file", "skill.zip",
                "application/zip", new byte[]{0x50, 0x4B, 0x03, 0x04});

        mockMvc.perform(multipart("/api/cli/v1/skills/global/publish/validate")
                        .file(file)
                        .file(new MockMultipartFile("visibility", "", "text/plain", "PRIVATE".getBytes()))
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true));
    }

    @Test
    void validatePublish_rejectsInvalidVisibility() throws Exception {
        givenValidPublishToken();
        MockMultipartFile file = new MockMultipartFile("file", "skill.zip",
                "application/zip", new byte[]{0x50, 0x4B, 0x03, 0x04});

        mockMvc.perform(multipart("/api/cli/v1/skills/global/publish/validate")
                        .file(file)
                        .file(new MockMultipartFile("visibility", "", "text/plain", "BOGUS".getBytes()))
                        .header("Authorization", "Bearer test-token"))
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
