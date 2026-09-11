package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.dto.SkillSuiteBundleOperationResponse;
import com.iflytek.skillhub.dto.SkillSuiteBundlePreviewResponse;
import com.iflytek.skillhub.service.bundle.SkillSuiteBundleConfirmationAppService;
import com.iflytek.skillhub.service.bundle.SkillSuiteBundlePreviewAppService;
import com.iflytek.skillhub.service.bundle.SkillSuiteBundleResponseMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class SkillSuiteBundleControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private SkillSuiteBundlePreviewAppService previewService;
    @MockBean private SkillSuiteBundleConfirmationAppService confirmationService;
    @MockBean private SkillSuiteBundleResponseMapper responseMapper;
    @MockBean private NamespaceMemberRepository namespaceMemberRepository;
    @MockBean private DeviceAuthService deviceAuthService;

    @Test
    void previewRequiresAuthentication() throws Exception {
        mockMvc.perform(multipart("/api/v1/suite-bundles/preview")
                        .file(new MockMultipartFile(
                                "file", "bundle.zip", "application/zip", new byte[]{1}))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
        verify(previewService, never()).preview(any(), any(), any(), any());
    }

    @Test
    void authenticatedPreviewReturnsStructuredPlanWithoutStorageLocations() throws Exception {
        SkillSuiteBundlePreviewAppService.PreviewOutcome outcome =
                new SkillSuiteBundlePreviewAppService.PreviewOutcome(
                        "preview-1", Instant.parse("2026-09-11T09:00:00Z"), null, null);
        SkillSuiteBundlePreviewResponse response = new SkillSuiteBundlePreviewResponse(
                "preview-1", Instant.parse("2026-09-11T09:00:00Z"), true, null,
                List.of(), List.of(), List.of(), List.of(), "warning-digest");
        when(previewService.preview(any(), eq("actor"), eq(Map.of()), eq(Set.of())))
                .thenReturn(outcome);
        when(responseMapper.toResponse(outcome)).thenReturn(response);

        mockMvc.perform(multipart("/api/v1/suite-bundles/preview")
                        .file(new MockMultipartFile(
                                "file", "bundle.zip", "application/zip", new byte[]{1}))
                        .with(authentication(authToken("actor")))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.previewToken").value("preview-1"))
                .andExpect(jsonPath("$.data.confirmable").value(true))
                .andExpect(jsonPath("$.data.warningDigest").value("warning-digest"))
                .andExpect(jsonPath("$.data.archiveObjectKey").doesNotExist());
    }

    @Test
    void previewArchiveIsRequiredByTheHttpContract() throws Exception {
        mockMvc.perform(multipart("/api/v1/suite-bundles/preview")
                        .with(authentication(authToken("actor")))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
        verify(previewService, never()).preview(any(), any(), any(), any());
    }

    @Test
    void confirmationRequiresWarningDigestAndPassesIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/suite-bundles/previews/preview-1/confirm")
                        .with(authentication(authToken("actor")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verify(confirmationService, never()).confirm(any(), any(), any(), any(), any(), any());

        SkillSuiteBundleConfirmationAppService.ConfirmationOutcome outcome =
                new SkillSuiteBundleConfirmationAppService.ConfirmationOutcome(
                        "operation-1", "RUNNING", false);
        when(confirmationService.confirm(
                "preview-1", "request-1", "warning-digest", "actor", Map.of(), Set.of()))
                .thenReturn(outcome);
        when(responseMapper.toResponse(outcome)).thenReturn(
                new SkillSuiteBundleOperationResponse("operation-1", "RUNNING", false));

        mockMvc.perform(post("/api/v1/suite-bundles/previews/preview-1/confirm")
                        .with(authentication(authToken("actor")))
                        .with(csrf())
                        .header("Idempotency-Key", "request-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warningDigest\":\"warning-digest\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operationId").value("operation-1"))
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.replayed").value(false));
    }

    private UsernamePasswordAuthenticationToken authToken(String userId) {
        PlatformPrincipal principal = new PlatformPrincipal(
                userId, userId, userId + "@example.test", null, "local", Set.of());
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
