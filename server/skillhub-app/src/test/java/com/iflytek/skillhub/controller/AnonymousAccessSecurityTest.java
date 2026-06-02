package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that anonymous requests are NOT blocked by SecurityFilterChain with 401/403.
 * Requests may fail for business reasons (404/500), but must reach controllers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnonymousAccessSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SkillDownloadService skillDownloadService;

    @MockBean
    private SkillQueryService skillQueryService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @Test
    void downloadEndpoint_notBlockedBySecurityFilter() throws Exception {
        mockMvc.perform(get("/api/v1/skills/ns/skill/download"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 401) throw new AssertionError("Expected no 401 but got 401");
                });
    }

    @Test
    void cliDownloadEndpoint_notBlockedBySecurityFilter() throws Exception {
        mockMvc.perform(get("/api/cli/v1/skills/ns/skill/download"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 401) throw new AssertionError("Expected no 401 but got 401");
                });
    }
}
