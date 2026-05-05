package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.dto.SkillLabelDto;
import com.iflytek.skillhub.service.SkillLabelAppService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SkillLabelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SkillLabelAppService skillLabelAppService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @Test
    void listSkillLabelsShouldBeReadable() throws Exception {
        when(skillLabelAppService.listSkillLabels(eq("team"), eq("demo"), isNull(), eq(Map.of())))
                .thenReturn(List.of(new SkillLabelDto("official", "PRIVILEGED", "Official")));

        mockMvc.perform(get("/api/web/skills/team/demo/labels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].slug").value("official"))
                .andExpect(jsonPath("$.data[0].type").value("PRIVILEGED"));
    }

    @Test
    void attachLabel_returnsUpdatedLabel() throws Exception {
        when(skillLabelAppService.attachLabel(eq("team"), eq("demo"), eq("official"), eq("user-1"), eq(Map.of()), any()))
                .thenReturn(new SkillLabelDto("official", "PRIVILEGED", "Official"));

        mockMvc.perform(put("/api/web/skills/team/demo/labels/official")
                        .with(user("user-1"))
                        .requestAttr("userId", "user-1")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.slug").value("official"))
                .andExpect(jsonPath("$.data.type").value("PRIVILEGED"));
    }

    @Test
    void detachLabel_returnsSuccessMessage() throws Exception {
        when(skillLabelAppService.detachLabel(eq("team"), eq("demo"), eq("official"), eq("user-1"), eq(Map.of()), any()))
                .thenReturn(new com.iflytek.skillhub.dto.MessageResponse("Label detached"));

        mockMvc.perform(delete("/api/web/skills/team/demo/labels/official")
                        .with(user("user-1"))
                        .requestAttr("userId", "user-1")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.message").value("Label detached"));
    }
}
