package com.iflytek.skillhub.controller.cli;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.entity.ApiToken;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.ratelimit.RateLimit;
import com.iflytek.skillhub.service.cli.CliSkillAppService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CliSkillControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired NamespaceMemberRepository namespaceMemberRepository;
    @MockBean CliSkillAppService cliSkillAppService;
    @MockBean ApiTokenService apiTokenService;
    @MockBean UserAccountRepository userAccountRepository;
    @MockBean UserRoleBindingRepository userRoleBindingRepository;

    @Test
    void downloadRoutesUseDownloadRateLimit() throws Exception {
        Method latest = CliSkillController.class.getMethod(
                "downloadLatest", String.class, String.class, HttpServletRequest.class);
        Method version = CliSkillController.class.getMethod(
                "downloadVersion", String.class, String.class, String.class, HttpServletRequest.class);

        assertDownloadRateLimit(latest.getAnnotation(RateLimit.class));
        assertDownloadRateLimit(version.getAnnotation(RateLimit.class));
    }

    @Test
    void publishConsumesMultipartFormData() throws Exception {
        Method publish = CliSkillController.class.getMethod(
                "publish", String.class, MultipartFile.class, String.class, PlatformPrincipal.class);

        PostMapping mapping = publish.getAnnotation(PostMapping.class);
        assertNotNull(mapping);
        assertArrayEquals(new String[]{MediaType.MULTIPART_FORM_DATA_VALUE}, mapping.consumes());
    }

    @Test
    void searchReturnsCompactCliResponse() throws Exception {
        given(cliSkillAppService.search("pdf", 20, null, null)).willReturn(
                new CliSkillAppService.CliSearchResult(List.of(
                        new CliSkillAppService.CliSearchItem("global", "pdf-parser", "1.2.0", "Parse PDFs")
                ), 1, 20)
        );

        mockMvc.perform(get("/api/cli/v1/skills/search").param("q", "pdf").param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].namespace").value("global"))
                .andExpect(jsonPath("$.data.items[0].slug").value("pdf-parser"))
                .andExpect(jsonPath("$.data.items[0].latestVersion").value("1.2.0"));
    }

    @Test
    void searchRejectsInvalidBearerBeforeAnonymousAccess() throws Exception {
        givenInvalidBearerToken();
        given(cliSkillAppService.search("pdf", 20, null, null)).willReturn(
                new CliSkillAppService.CliSearchResult(List.of(), 0, 20)
        );

        mockMvc.perform(get("/api/cli/v1/skills/search")
                        .param("q", "pdf")
                        .param("limit", "20")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer unknown-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(cliSkillAppService);
    }

    @Test
    void searchWithValidBearerProjectsIdentityAndNamespaceRoles() throws Exception {
        ApiToken token = new ApiToken("user-cli-token", "cli", "sk_test", "hash", "[]");
        UserAccount user = new UserAccount("user-cli-token", "CLI User", "cli@example.com", "");
        Map<Long, NamespaceRole> nsRoles = Map.of(9L, NamespaceRole.MEMBER);

        given(apiTokenService.validateToken("raw-token")).willReturn(Optional.of(token));
        given(userAccountRepository.findById("user-cli-token")).willReturn(Optional.of(user));
        given(userRoleBindingRepository.findByUserId("user-cli-token")).willReturn(List.of());
        namespaceMemberRepository.save(new NamespaceMember(9L, "user-cli-token", NamespaceRole.MEMBER));
        given(cliSkillAppService.search("private", 20, "user-cli-token", nsRoles)).willReturn(
                new CliSkillAppService.CliSearchResult(List.of(), 0, 20)
        );

        mockMvc.perform(get("/api/cli/v1/skills/search")
                        .param("q", "private")
                        .param("limit", "20")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer raw-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());

        verify(cliSkillAppService).search("private", 20, "user-cli-token", nsRoles);
        verify(apiTokenService).touchLastUsed(token);
    }

    @Test
    void namespaceSyncReturnsCursorResponseForValidBearer() throws Exception {
        ApiToken token = new ApiToken("sync-user", "cli", "sk_test", "hash", "[]");
        UserAccount user = new UserAccount("sync-user", "Sync User", "sync@example.com", "");
        var response = new com.iflytek.skillhub.dto.cli.CliNamespaceSyncResponse(
                List.of(new com.iflytek.skillhub.dto.cli.CliNamespaceSyncItemResponse(
                        "team-a", "demo", "1.0.0", 42L, "sha256:fingerprint",
                        java.time.Instant.parse("2026-08-20T00:00:00Z"), "NAMESPACE_ONLY",
                        "/api/v1/skills/team-a/demo/versions/1.0.0/download"
                )),
                "2"
        );

        given(apiTokenService.validateToken("sync-token")).willReturn(Optional.of(token));
        given(userAccountRepository.findById("sync-user")).willReturn(Optional.of(user));
        given(userRoleBindingRepository.findByUserId("sync-user")).willReturn(List.of());
        given(cliSkillAppService.listNamespaceSkills("team-a", 1, 25, "sync-user", Map.of()))
                .willReturn(response);

        mockMvc.perform(get("/api/cli/v1/namespaces/team-a/skills")
                        .param("cursor", "1")
                        .param("limit", "25")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer sync-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].namespace").value("team-a"))
                .andExpect(jsonPath("$.data.items[0].slug").value("demo"))
                .andExpect(jsonPath("$.data.items[0].version").value("1.0.0"))
                .andExpect(jsonPath("$.data.nextCursor").value("2"));

        verify(cliSkillAppService).listNamespaceSkills("team-a", 1, 25, "sync-user", Map.of());
    }

    @Test
    void resolveReturnsCliResolveResponse() throws Exception {
        given(cliSkillAppService.resolve("global", "demo", null, null, null)).willReturn(
                new com.iflytek.skillhub.dto.cli.CliResolveResponse(
                        "global", "demo", "2.0.0", 42L, "abc123",
                        "/api/v1/skills/global/demo/versions/2.0.0/download"
                )
        );

        mockMvc.perform(get("/api/cli/v1/skills/global/demo/resolve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.namespace").value("global"))
                .andExpect(jsonPath("$.data.slug").value("demo"))
                .andExpect(jsonPath("$.data.version").value("2.0.0"))
                .andExpect(jsonPath("$.data.versionId").value(42))
                .andExpect(jsonPath("$.data.fingerprint").value("abc123"));
    }

    @Test
    void resolveRejectsInvalidBearerBeforeAnonymousAccess() throws Exception {
        givenInvalidBearerToken();
        given(cliSkillAppService.resolve("global", "demo", null, null, null)).willReturn(
                new com.iflytek.skillhub.dto.cli.CliResolveResponse(
                        "global", "demo", "2.0.0", 42L, "abc123",
                        "/api/v1/skills/global/demo/versions/2.0.0/download"
                )
        );

        mockMvc.perform(get("/api/cli/v1/skills/global/demo/resolve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer unknown-token"))
                .andExpect(status().isUnauthorized());

        verify(cliSkillAppService, never()).resolve(any(), any(), any(), any(), any());
    }

    @Test
    void downloadLatestRejectsInvalidBearerBeforeAnonymousAccess() throws Exception {
        givenInvalidBearerToken();
        given(cliSkillAppService.downloadLatest(any(), any(), any())).willReturn(downloadResponse());

        mockMvc.perform(get("/api/cli/v1/skills/global/demo/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer unknown-token"))
                .andExpect(status().isUnauthorized());

        verify(cliSkillAppService, never()).downloadLatest(any(), any(), any());
    }

    @Test
    void downloadVersionRejectsInvalidBearerBeforeAnonymousAccess() throws Exception {
        givenInvalidBearerToken();
        given(cliSkillAppService.downloadVersion(any(), any(), any(), any())).willReturn(downloadResponse());

        mockMvc.perform(get("/api/cli/v1/skills/global/demo/versions/1.0.0/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer unknown-token"))
                .andExpect(status().isUnauthorized());

        verify(cliSkillAppService, never()).downloadVersion(any(), any(), any(), any());
    }

    @Test
    void deleteRequiresAuthentication() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/cli/v1/skills/global/demo"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteReturnsCliDeleteResponse() throws Exception {
        ApiToken token = new ApiToken("user-1", "cli", "sk_test", "hash", "[\"skill:delete\"]");
        UserAccount user = new UserAccount("user-1", "tester", "t@example.com", "");

        given(apiTokenService.validateToken("test-token")).willReturn(Optional.of(token));
        given(userAccountRepository.findById("user-1")).willReturn(Optional.of(user));
        given(userRoleBindingRepository.findByUserId("user-1")).willReturn(List.of());
        given(cliSkillAppService.deleteRemote(
                org.mockito.ArgumentMatchers.eq("global"),
                org.mockito.ArgumentMatchers.eq("demo"),
                org.mockito.ArgumentMatchers.eq("user-1"),
                org.mockito.ArgumentMatchers.any()
        )).willReturn(new com.iflytek.skillhub.dto.cli.CliDeleteResponse(
                true, "remote", "delete", "global", "demo"
        ));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/cli/v1/skills/global/demo")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true))
                .andExpect(jsonPath("$.data.namespace").value("global"))
                .andExpect(jsonPath("$.data.slug").value("demo"));
    }

    private static void assertDownloadRateLimit(RateLimit rateLimit) {
        assertNotNull(rateLimit);
        assertEquals("download", rateLimit.category());
        assertEquals(120, rateLimit.authenticated());
        assertEquals(30, rateLimit.anonymous());
    }

    private static ResponseEntity<InputStreamResource> downloadResponse() {
        return ResponseEntity.ok(new InputStreamResource(new ByteArrayInputStream("zip".getBytes())));
    }

    private void givenInvalidBearerToken() {
        given(apiTokenService.validateToken("unknown-token")).willReturn(Optional.empty());
    }
}
