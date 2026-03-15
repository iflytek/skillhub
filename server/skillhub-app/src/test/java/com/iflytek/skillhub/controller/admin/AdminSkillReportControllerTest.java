package com.iflytek.skillhub.controller.admin;

import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.report.SkillReport;
import com.iflytek.skillhub.domain.report.SkillReportService;
import com.iflytek.skillhub.dto.AdminSkillReportSummaryResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.service.AdminSkillReportAppService;
import java.time.LocalDateTime;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class AdminSkillReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminSkillReportAppService adminSkillReportAppService;

    @MockBean
    private SkillReportService skillReportService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @Test
    void listReports_returnsPagedReports() throws Exception {
        when(adminSkillReportAppService.listReports("PENDING", 0, 20))
                .thenReturn(new PageResponse<>(
                        List.of(new AdminSkillReportSummaryResponse(
                                99L,
                                10L,
                                "global",
                                "demo-skill",
                                "Demo Skill",
                                "user-1",
                                "Spam",
                                "details",
                                "PENDING",
                                null,
                                null,
                                LocalDateTime.of(2026, 3, 15, 12, 0),
                                null
                        )),
                        1,
                        0,
                        20
                ));

        mockMvc.perform(get("/api/v1/admin/skill-reports")
                        .param("status", "PENDING")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].id").value(99))
                .andExpect(jsonPath("$.data.items[0].skillSlug").value("demo-skill"));
    }

    @Test
    void resolveReport_returnsUpdatedEnvelope() throws Exception {
        SkillReport report = new SkillReport(10L, 1L, "user-1", "Spam", "details");
        ReflectionTestUtils.setField(report, "id", 99L);
        report.setStatus(com.iflytek.skillhub.domain.report.SkillReportStatus.RESOLVED);
        when(skillReportService.resolveReport(org.mockito.ArgumentMatchers.eq(99L), org.mockito.ArgumentMatchers.eq("admin"), org.mockito.ArgumentMatchers.eq("handled"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(report);

        mockMvc.perform(post("/api/v1/admin/skill-reports/99/resolve")
                        .with(authentication(adminAuth()))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"comment\":\"handled\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportId").value(99))
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));
    }

    private UsernamePasswordAuthenticationToken adminAuth() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "admin", "admin", "admin@example.com", "", "github", Set.of("SKILL_ADMIN")
        );
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_SKILL_ADMIN"))
        );
    }
}
