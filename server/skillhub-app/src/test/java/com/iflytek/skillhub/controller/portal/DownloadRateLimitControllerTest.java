package com.iflytek.skillhub.controller.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.ratelimit.RateLimiter;
import java.io.ByteArrayInputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class DownloadRateLimitControllerTest {

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
    private RateLimiter rateLimiter;

    @Test
    void anonymousDownloadUsesIpAndSignedCookieBuckets() throws Exception {
        given(rateLimiter.tryAcquire(anyString(), anyInt(), anyInt())).willReturn(true);
        given(skillDownloadService.downloadVersion("global", "demo-skill", "1.0.0", null, Map.of()))
                .willReturn(new SkillDownloadService.DownloadResult(
                        new ByteArrayInputStream("zip".getBytes()),
                        "demo-skill-1.0.0.zip",
                        3L,
                        "application/zip",
                        null
                ));

        var result = mockMvc.perform(get("/api/v1/skills/global/demo-skill/versions/1.0.0/download")
                        .header("X-Forwarded-For", "203.0.113.10"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"demo-skill-1.0.0.zip\""))
                .andExpect(header().exists("Set-Cookie"))
                .andReturn();

        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .anySatisfy(cookie -> {
                    assertThat(cookie).contains("skillhub_anon_dl=");
                    assertThat(cookie).contains("HttpOnly");
                    assertThat(cookie).contains("SameSite=Lax");
                });

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rateLimiter, times(2)).tryAcquire(keyCaptor.capture(), anyInt(), anyInt());
        assertThat(keyCaptor.getAllValues()).anyMatch(key -> key.startsWith("ratelimit:download:ip:"));
        assertThat(keyCaptor.getAllValues()).anyMatch(key -> key.startsWith("ratelimit:download:anon:"));
    }

    @Test
    void anonymousDownloadReturnsTooManyRequestsWhenIpBucketIsExceeded() throws Exception {
        given(rateLimiter.tryAcquire(anyString(), anyInt(), anyInt())).willAnswer(invocation ->
                ((String) invocation.getArgument(0)).startsWith("ratelimit:download:ip:") ? false : true);

        mockMvc.perform(get("/api/v1/skills/global/demo-skill/versions/1.0.0/download")
                        .header("X-Forwarded-For", "203.0.113.10"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429));

        verify(skillDownloadService, never()).downloadVersion(anyString(), anyString(), anyString(), anyString(), any());
    }
}
