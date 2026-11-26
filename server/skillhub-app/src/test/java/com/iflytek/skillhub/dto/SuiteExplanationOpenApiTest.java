package com.iflytek.skillhub.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.search.SearchRebuildService;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"skillhub.builtin-skills.enabled=false", "skillhub.bootstrap.admin.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class SuiteExplanationOpenApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean ObjectStorageService storage;
    @MockBean SearchRebuildService search;
    @MockBean JavaMailSender mail;

    @Test
    void exposesPinnedSuiteExplanation() throws Exception {
        byte[] document = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(mapper.readTree(document).at("/paths/~1api~1web~1suites~1{namespace}~1{slug}~1explanation/post/operationId")
                .asText()).isEqualTo("explainSkillSuite");
        String output = System.getProperty("skillhub.test.openapi-output");
        if (output != null) Files.write(Path.of(output), document);
    }
}
