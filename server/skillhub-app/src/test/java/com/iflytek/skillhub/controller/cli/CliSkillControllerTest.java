package com.iflytek.skillhub.controller.cli;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.ratelimit.RateLimit;
import com.iflytek.skillhub.service.cli.CliSkillAppService;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CliSkillControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean CliSkillAppService cliSkillAppService;

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
    void deleteRequiresAuthentication() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/cli/v1/skills/global/demo"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteReturnsCliDeleteResponse() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "tester", "t@example.com", "", "api_token", Set.of("USER"));
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("SCOPE_skill:delete")));

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
                        .with(authentication(auth)))
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
}
