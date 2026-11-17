package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.dto.BatchReviewResponse;
import com.iflytek.skillhub.service.BatchReviewAppService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BatchReviewControllerTest {
    @Autowired MockMvc mvc;
    @MockBean BatchReviewAppService batch;
    @MockBean RbacService rbac;
    RequestPostProcessor auth(String provider) {
        return authentication(new UsernamePasswordAuthenticationToken(
                new PlatformPrincipal("admin","Admin",null,null,provider,Set.of()),null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"),new SimpleGrantedAuthority("SCOPE_skill:manage"))));
    }
    @Test void anonymousAndMissingCsrfCannotWrite() throws Exception {
        mvc.perform(post("/api/web/reviews/batch-decide").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[1],\"action\":\"APPROVE\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/web/reviews/batch-decide").with(auth("session")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[1],\"action\":\"APPROVE\"}")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.msg").value("Session expired"));
        verifyNoInteractions(batch);
    }
    @Test void tokenCannotUseEitherBatchRouteEvenWithManageScope() throws Exception {
        for(String route:List.of("/api/v1/reviews/batch-decide","/api/web/reviews/batch-preview")) {
            mvc.perform(post(route).with(auth("api_token")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ids\":[1],\"action\":\"APPROVE\"}")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(batch);
    }
    @Test void validatesEmptyOversizedAndInvalidIdsAndActionBeforeDispatch() throws Exception {
        for(String body:List.of("{\"ids\":[],\"action\":\"APPROVE\"}","{\"ids\":[0],\"action\":\"APPROVE\"}",
                "{\"ids\":[null],\"action\":\"APPROVE\"}","{\"ids\":[1],\"action\":\"OTHER\"}",
                "{\"ids\":"+LongStream.rangeClosed(1,51).boxed().toList()+",\"action\":\"APPROVE\"}")) {
            mvc.perform(post("/api/web/reviews/batch-decide").with(auth("session")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(batch);
    }
    @Test void returnsPerItemResultsAndUsesAuthenticatedActor() throws Exception {
        when(batch.process(any(),eq("admin"),any(),eq(false))).thenReturn(new BatchReviewResponse(List.of(
                new BatchReviewResponse.BatchReviewItem(1L,"APPROVED","review.batch.approved","Approved"),
                new BatchReviewResponse.BatchReviewItem(2L,"SKIPPED","review.no_permission","Not allowed"))));
        mvc.perform(post("/api/web/reviews/batch-decide").with(auth("session")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[1,2],\"action\":\"APPROVE\",\"actor\":\"forged\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].outcome").value("APPROVED"))
                .andExpect(jsonPath("$.data.items[1].outcome").value("SKIPPED"));
    }
    @Test void exportsActualOpenApiWhenRequested() throws Exception {
        String path=System.getProperty("skillhub.openapi.output");
        if(path!=null) {
            String json=mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            Files.writeString(Path.of(path),json);
        }
    }
}
