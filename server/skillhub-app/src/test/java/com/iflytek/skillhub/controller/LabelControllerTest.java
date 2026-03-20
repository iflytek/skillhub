package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.dto.SkillLabelDto;
import com.iflytek.skillhub.service.PublicLabelAppService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LabelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PublicLabelAppService publicLabelAppService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @Test
    void listVisibleLabelsShouldUseUnifiedEnvelope() throws Exception {
        when(publicLabelAppService.listVisibleFilters())
                .thenReturn(List.of(
                        new SkillLabelDto("code-generation", "RECOMMENDED", "Code Generation"),
                        new SkillLabelDto("verified", "PRIVILEGED", "Verified")
                ));

        mockMvc.perform(get("/api/web/labels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].slug").value("code-generation"))
                .andExpect(jsonPath("$.data[0].displayName").value("Code Generation"))
                .andExpect(jsonPath("$.data[1].slug").value("verified"))
                .andExpect(jsonPath("$.data[1].type").value("PRIVILEGED"))
                .andExpect(jsonPath("$.data[1].displayName").value("Verified"));
    }

    @Test
    void listVisibleLabelsShouldAlsoBePublicOnV1Path() throws Exception {
        when(publicLabelAppService.listVisibleFilters())
                .thenReturn(List.of(new SkillLabelDto("code-generation", "RECOMMENDED", "Code Generation")));

        mockMvc.perform(get("/api/v1/labels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].slug").value("code-generation"))
                .andExpect(jsonPath("$.data[0].displayName").value("Code Generation"));
    }
}
