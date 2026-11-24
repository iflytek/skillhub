package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.githubsync.GithubPackageReportResult;
import com.iflytek.skillhub.service.GithubSyncUploadAppService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Optional source tag provenance must never turn an otherwise valid package report into a 400. */
class GithubSyncUploadControllerSourceTagTest {
    @Test
    void anySourceTagShapeIsAcceptedWithThePackage() throws Exception {
        GithubSyncUploadAppService service = mock(GithubSyncUploadAppService.class);
        when(service.report(any(), anyLong(), any(), any(), any(), any(), any(), anyLong(), any(), any()))
                .thenReturn(new GithubPackageReportResult(GithubPackageReportResult.Status.RECEIVED,
                        UUID.randomUUID(), null));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new GithubSyncUploadController(service))
                .setControllerAdvice(new GithubSyncInternalErrorHandler()).build();
        String report = """
                {"lease":{"generation":1,"token":"%s"},"admissionId":"%s","commitSha":"%s",
                 "skillPath":"skills/demo","uploadId":"%s","sizeBytes":12,"sha256":"%s"%s}
                """;
        for (String sourceTag : List.of("", ",\"sourceTag\":{\"status\":\"NO_TAG\"}",
                ",\"sourceTag\":{\"status\":\"AFTER_TAG\",\"commitsAfterTag\":2147483648}",
                ",\"sourceTag\":{\"status\":\"AFTER_TAG\",\"commitsAfterTag\":\"3\"}",
                ",\"sourceTag\":{\"status\":{\"nested\":true}}", ",\"sourceTag\":[]")) {
            mvc.perform(post("/internal/github-sync/v1/tasks/" + UUID.randomUUID() + "/packages/report")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(report.formatted("a".repeat(64), UUID.randomUUID(), "b".repeat(40),
                                    UUID.randomUUID(), "c".repeat(64), sourceTag)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("RECEIVED"));
        }
    }
}
