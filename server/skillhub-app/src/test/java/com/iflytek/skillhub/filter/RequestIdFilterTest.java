package com.iflytek.skillhub.filter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestIdFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldGenerateRequestIdWhenNotProvided() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void shouldPreserveProvidedRequestId() throws Exception {
        String requestId = "test-request-123";
        mockMvc.perform(get("/api/v1/health")
                        .header("X-Request-Id", requestId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", requestId))
                .andExpect(jsonPath("$.requestId").value(requestId));
    }

    @Test
    void shouldPreserveRequestIdAtMaximumLength() throws Exception {
        String requestId = "a".repeat(64);

        mockMvc.perform(get("/api/v1/health")
                        .header("X-Request-Id", requestId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", requestId))
                .andExpect(jsonPath("$.requestId").value(requestId));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "-starts-with-symbol",
            "contains space",
            "contains/slash",
            "包含中文"
    })
    void shouldReplaceInvalidRequestId(String requestId) throws Exception {
        assertInvalidRequestIdIsReplaced(requestId);
    }

    @Test
    void shouldReplaceRequestIdLongerThanMaximumLength() throws Exception {
        assertInvalidRequestIdIsReplaced("a".repeat(65));
    }

    private void assertInvalidRequestIdIsReplaced(String invalidRequestId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/health")
                        .header("X-Request-Id", invalidRequestId))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andReturn();

        String effectiveRequestId = result.getResponse().getHeader("X-Request-Id");
        assertThat(effectiveRequestId)
                .isNotEqualTo(invalidRequestId)
                .matches("^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$");
        assertThat(result.getResponse().getContentAsString()).contains(effectiveRequestId);
    }
}
