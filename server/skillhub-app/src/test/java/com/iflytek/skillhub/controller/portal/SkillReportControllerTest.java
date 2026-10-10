package com.iflytek.skillhub.controller.portal;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.report.SkillReport;
import com.iflytek.skillhub.domain.report.SkillReportService;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillSlugResolutionService;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class SkillReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NamespaceRepository namespaceRepository;

    @MockBean
    private SkillRepository skillRepository;

    @MockBean
    private SkillReportService skillReportService;

    @MockBean
    private SkillSlugResolutionService skillSlugResolutionService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @Test
    void submitReport_returnsCreatedEnvelope() throws Exception {
        Namespace namespace = new Namespace("global", "Global", "owner");
        ReflectionTestUtils.setField(namespace, "id", 1L);
        Skill skill = new Skill(1L, "demo-skill", "owner", SkillVisibility.PUBLIC);
        ReflectionTestUtils.setField(skill, "id", 10L);
        SkillReport report = new SkillReport(10L, 1L, "user-1", "Spam", "details");
        ReflectionTestUtils.setField(report, "id", 99L);

        given(namespaceRepository.findBySlug("global")).willReturn(java.util.Optional.of(namespace));
        given(skillSlugResolutionService.resolve(1L, "demo-skill", "user-1", SkillSlugResolutionService.Preference.PUBLISHED))
                .willReturn(skill);
        given(skillReportService.submitReport(eq(10L), eq("user-1"), eq("Spam"), eq("details"), nullable(String.class), nullable(String.class)))
                .willReturn(report);

        mockMvc.perform(post("/api/web/skills/global/demo-skill/reports")
                        .requestAttr("userId", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Spam\",\"details\":\"details\"}")
                        .with(user("user-1"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.reportId").value(99))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }
}
