package com.iflytek.skillhub.controller.portal;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.metrics.SkillHubMetrics;
import com.iflytek.skillhub.ratelimit.RateLimiter;
import com.iflytek.skillhub.storage.StorageAccessException;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class SkillControllerDownloadTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SkillQueryService skillQueryService;

    @MockBean
    private SkillDownloadService skillDownloadService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @MockBean
    private SkillHubMetrics skillHubMetrics;

    @MockBean
    private RateLimiter rateLimiter;

    @Test
    void downloadVersion_redirectsToPresignedUrlWhenAvailable() throws Exception {
        given(rateLimiter.tryAcquire(anyString(), anyInt(), anyInt())).willReturn(true);
        given(skillDownloadService.downloadVersion("global", "demo-skill", "1.0.0", null, java.util.Map.of()))
            .willReturn(new SkillDownloadService.DownloadResult(
                () -> new ByteArrayInputStream("zip".getBytes()),
                "demo-skill-1.0.0.zip",
                128L,
                "application/zip",
                "https://download.example/presigned",
                false
            ));

        mockMvc.perform(get("/api/v1/skills/global/demo-skill/versions/1.0.0/download")
                .with(user("test-user"))
                .with(csrf()))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "https://download.example/presigned"));
    }

    @Test
    void downloadVersion_streamsWhenPresignedUrlIsInsecureForHttpsRequest() throws Exception {
        given(rateLimiter.tryAcquire(anyString(), anyInt(), anyInt())).willReturn(true);
        given(skillDownloadService.downloadVersion("global", "demo-skill", "1.0.0", null, java.util.Map.of()))
            .willReturn(new SkillDownloadService.DownloadResult(
                () -> new ByteArrayInputStream("zip".getBytes()),
                "demo-skill-1.0.0.zip",
                3L,
                "application/zip",
                "http://download.example/presigned",
                false
            ));

        mockMvc.perform(get("/api/v1/skills/global/demo-skill/versions/1.0.0/download")
                .header("X-Forwarded-Proto", "https")
                .with(user("test-user"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"demo-skill-1.0.0.zip\""));
    }

    @Test
    void downloadVersion_streamsWhenPresignedUrlUnavailable() throws Exception {
        given(rateLimiter.tryAcquire(anyString(), anyInt(), anyInt())).willReturn(true);
        given(skillDownloadService.downloadVersion("global", "demo-skill", "1.0.0", null, java.util.Map.of()))
            .willReturn(new SkillDownloadService.DownloadResult(
                () -> new ByteArrayInputStream("zip".getBytes()),
                "demo-skill-1.0.0.zip",
                3L,
                "application/zip",
                null,
                false
            ));

        mockMvc.perform(get("/api/v1/skills/global/demo-skill/versions/1.0.0/download")
                .with(user("test-user"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"demo-skill-1.0.0.zip\""));
    }

    @Test
    void downloadVersion_allowsAnonymousForGlobalSkill() throws Exception {
        given(rateLimiter.tryAcquire(anyString(), anyInt(), anyInt())).willReturn(true);
        given(skillDownloadService.downloadVersion("global", "demo-skill", "1.0.0", null, java.util.Map.of()))
            .willReturn(new SkillDownloadService.DownloadResult(
                () -> new ByteArrayInputStream("zip".getBytes()),
                "demo-skill-1.0.0.zip",
                3L,
                "application/zip",
                null,
                false
            ));

        mockMvc.perform(get("/api/v1/skills/global/demo-skill/versions/1.0.0/download")
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"demo-skill-1.0.0.zip\""));
    }

    @Test
    void downloadVersion_forbidsAnonymousWhenServiceRejectsSkill() throws Exception {
        given(rateLimiter.tryAcquire(anyString(), anyInt(), anyInt())).willReturn(true);
        given(skillDownloadService.downloadVersion("team-ai", "demo-skill", "1.0.0", null, java.util.Map.of()))
            .willThrow(new DomainForbiddenException("error.skill.access.denied", "demo-skill"));

        mockMvc.perform(get("/api/v1/skills/team-ai/demo-skill/versions/1.0.0/download")
                .with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    void downloadVersion_redirectDoesNotOpenContentStream() throws Exception {
        given(rateLimiter.tryAcquire(anyString(), anyInt(), anyInt())).willReturn(true);
        given(skillDownloadService.downloadVersion("global", "demo-skill", "1.0.0", null, java.util.Map.of()))
            .willReturn(new SkillDownloadService.DownloadResult(
                () -> {
                    throw new AssertionError("content stream should not be opened for redirects");
                },
                "demo-skill-1.0.0.zip",
                128L,
                "application/zip",
                "https://download.example/presigned",
                false
            ));

        mockMvc.perform(get("/api/v1/skills/global/demo-skill/versions/1.0.0/download")
                .with(user("test-user"))
                .with(csrf()))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "https://download.example/presigned"));
    }

    @Test
    void downloadVersion_usesPerVersionRateLimitKey() throws Exception {
        given(rateLimiter.tryAcquire(anyString(), anyInt(), anyInt())).willReturn(true);
        given(skillDownloadService.downloadVersion("global", "demo-skill", "1.0.0", "test-user", java.util.Map.of()))
            .willReturn(new SkillDownloadService.DownloadResult(
                () -> new ByteArrayInputStream("zip".getBytes()),
                "demo-skill-1.0.0.zip",
                3L,
                "application/zip",
                null,
                false
            ));

        mockMvc.perform(get("/api/v1/skills/global/demo-skill/versions/1.0.0/download")
                .requestAttr("userId", "test-user")
                .with(user("test-user"))
                .with(csrf()))
            .andExpect(status().isOk());

        verify(rateLimiter).tryAcquire(
                "ratelimit:download:user:test-user:ns:global:slug:demo-skill:version:1.0.0",
                30,
                60
        );
    }

    @Test
    void downloadVersion_returnsServiceUnavailableWhenStorageFails() throws Exception {
        given(rateLimiter.tryAcquire(anyString(), anyInt(), anyInt())).willReturn(true);
        given(skillDownloadService.downloadVersion("global", "demo-skill", "1.0.0", null, java.util.Map.of()))
            .willThrow(new StorageAccessException("getObject", "packages/1/10/bundle.zip", new RuntimeException("boom")));

        mockMvc.perform(get("/api/v1/skills/global/demo-skill/versions/1.0.0/download")
                .with(user("test-user"))
                .with(csrf()))
            .andExpect(status().isServiceUnavailable());
    }
}
