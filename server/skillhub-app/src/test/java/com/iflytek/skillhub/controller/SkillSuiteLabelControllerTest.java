package com.iflytek.skillhub.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.dto.SkillLabelDto;
import com.iflytek.skillhub.service.SkillSuiteLabelAppService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SkillSuiteLabelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SkillSuiteLabelAppService skillSuiteLabelAppService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @Test
    void listSuiteLabelsShouldBeReadableThroughWebContract() throws Exception {
        when(skillSuiteLabelAppService.listLabels(
                eq("team"), eq("workflow"), isNull(), eq(Map.of()), eq(Set.of())))
                .thenReturn(List.of(new SkillLabelDto("healthcare", "RECOMMENDED", "医疗健康")));

        mockMvc.perform(get("/api/web/suites/team/workflow/labels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].slug").value("healthcare"))
                .andExpect(jsonPath("$.data[0].displayName").value("医疗健康"));
    }
}
