package com.iflytek.skillhub.controller.admin;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.user.ProfileChangeRequest;
import com.iflytek.skillhub.domain.user.ProfileChangeStatus;
import com.iflytek.skillhub.domain.user.ProfileReviewService;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.ProfileReviewSummaryResponse;
import com.iflytek.skillhub.service.AdminProfileReviewAppService;
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

import java.time.Instant;
import java.util.List;
import java.util.Set;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminProfileReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminProfileReviewAppService appService;

    @MockBean
    private ProfileReviewService reviewService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    // -- Helpers --

    private static UsernamePasswordAuthenticationToken userAdminAuth() {
        var principal = new PlatformPrincipal("admin-1", "Admin", "admin@example.com", "", "github", Set.of("USER_ADMIN"));
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER_ADMIN")));
    }

    private static UsernamePasswordAuthenticationToken superAdminAuth() {
        var principal = new PlatformPrincipal("super-1", "Super", "super@example.com", "", "github", Set.of("SUPER_ADMIN"));
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
    }

    private static UsernamePasswordAuthenticationToken userAuth() {
        var principal = new PlatformPrincipal("user-1", "User", "user@example.com", "", "github", Set.of("USER"));
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private static UsernamePasswordAuthenticationToken skillAdminAuth() {
        var principal = new PlatformPrincipal("skill-1", "SkillAdmin", "skill@example.com", "", "github", Set.of("SKILL_ADMIN"));
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_SKILL_ADMIN")));
    }

    private static UsernamePasswordAuthenticationToken auditorAuth() {
        var principal = new PlatformPrincipal("auditor-1", "Auditor", "auditor@example.com", "", "github", Set.of("AUDITOR"));
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_AUDITOR")));
    }

    private static ProfileChangeRequest approvedRequest() {
        var req = new ProfileChangeRequest("user-1", "{\"displayName\":\"New\"}", "{\"displayName\":\"Old\"}",
                ProfileChangeStatus.APPROVED, "PASS", null);
        req.setReviewerId("admin-1");
        req.setReviewedAt(Instant.now());
        try {
            var idField = ProfileChangeRequest.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(req, 1L);
        } catch (Exception e) { throw new RuntimeException(e); }
        return req;
    }

    private static ProfileReviewSummaryResponse sampleSummary() {
        return new ProfileReviewSummaryResponse(1L, "user-1", "testuser", "OldName", "NewName",
                "PENDING", "PASS", null, null, null, Instant.now(), null);
    }

    // ===== Positive: list pending as USER_ADMIN =====

    @Test
    void listPendingReviews_asUserAdmin_returnsPage() throws Exception {
        given(appService.list(eq("PENDING"), eq(0), eq(20), eq("DESC")))
                .willReturn(new PageResponse<>(List.of(sampleSummary()), 1, 0, 20));

        mockMvc.perform(get("/api/v1/admin/profile-reviews")
                        .param("status", "PENDING")
                        .with(authentication(userAdminAuth()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].username").value("testuser"));
    }

    // ===== Positive: approve as USER_ADMIN =====

    @Test
    void approveReview_asUserAdmin_appliesChange() throws Exception {
        given(reviewService.approve(eq(1L), eq("admin-1"), any(), any(), any()))
                .willReturn(approvedRequest());

        mockMvc.perform(post("/api/v1/admin/profile-reviews/1/approve")
                        .with(authentication(userAdminAuth()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    // ===== Positive: reject as USER_ADMIN =====

    @Test
    void rejectReview_asUserAdmin_recordsComment() throws Exception {
        var rejected = new ProfileChangeRequest("user-1", "{\"displayName\":\"New\"}", "{\"displayName\":\"Old\"}",
                ProfileChangeStatus.REJECTED, "PASS", null);
        rejected.setReviewComment("Not appropriate");
        try {
            var idField = ProfileChangeRequest.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(rejected, 1L);
        } catch (Exception e) { throw new RuntimeException(e); }

        given(reviewService.reject(eq(1L), eq("admin-1"), eq("Not appropriate"), any(), any(), any()))
                .willReturn(rejected);

        mockMvc.perform(post("/api/v1/admin/profile-reviews/1/reject")
                        .with(authentication(userAdminAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Not appropriate\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    // ===== Positive: approve as SUPER_ADMIN =====

    @Test
    void approveReview_asSuperAdmin_success() throws Exception {
        given(reviewService.approve(eq(1L), eq("super-1"), any(), any(), any()))
                .willReturn(approvedRequest());

        mockMvc.perform(post("/api/v1/admin/profile-reviews/1/approve")
                        .with(authentication(superAdminAuth()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ===== Positive: filter by status =====

    @Test
    void listReviews_filterByStatus() throws Exception {
        given(appService.list(eq("APPROVED"), eq(0), eq(20), eq("DESC")))
                .willReturn(new PageResponse<>(List.of(), 0, 0, 20));

        mockMvc.perform(get("/api/v1/admin/profile-reviews")
                        .param("status", "APPROVED")
                        .with(authentication(userAdminAuth()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    // ===== Positive: pagination =====

    @Test
    void listReviews_pagination() throws Exception {
        given(appService.list(isNull(), eq(2), eq(5), eq("DESC")))
                .willReturn(new PageResponse<>(List.of(), 0, 2, 5));

        mockMvc.perform(get("/api/v1/admin/profile-reviews")
                        .param("page", "2")
                        .param("size", "5")
                        .with(authentication(userAdminAuth()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2));
    }

    @Test
    void listReviews_forwardsSortDirection() throws Exception {
        given(appService.list(eq("PENDING"), eq(0), eq(20), eq("ASC")))
                .willReturn(new PageResponse<>(List.of(), 0, 0, 20));

        mockMvc.perform(get("/api/v1/admin/profile-reviews")
                        .param("status", "PENDING")
                        .param("sortDirection", "ASC")
                        .with(authentication(userAdminAuth()))
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(appService).list("PENDING", 0, 20, "ASC");
    }

    // ===== Error: approve already approved returns 400 =====

    @Test
    void approveReview_alreadyApproved_returns400() throws Exception {
        given(reviewService.approve(eq(1L), eq("admin-1"), any(), any(), any()))
                .willThrow(new DomainBadRequestException("error.profileReview.notPending"));

        mockMvc.perform(post("/api/v1/admin/profile-reviews/1/approve")
                        .with(authentication(userAdminAuth()))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    // ===== Error: approve not found returns 404 =====

    @Test
    void approveReview_notFound_returns404() throws Exception {
        given(reviewService.approve(eq(99L), eq("admin-1"), any(), any(), any()))
                .willThrow(new DomainNotFoundException("error.profileReview.notFound"));

        mockMvc.perform(post("/api/v1/admin/profile-reviews/99/approve")
                        .with(authentication(userAdminAuth()))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ===== Security: USER role returns 403 =====

    @Test
    void listReviews_asUser_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/profile-reviews")
                        .with(authentication(userAuth()))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ===== Security: unauthenticated returns 401 =====

    @Test
    void listReviews_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/profile-reviews"))
                .andExpect(status().isUnauthorized());
    }

    // ===== Security: SKILL_ADMIN returns 403 =====

    @Test
    void listReviews_asSkillAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/profile-reviews")
                        .with(authentication(skillAdminAuth()))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ===== Security: AUDITOR returns 403 =====

    @Test
    void listReviews_asAuditor_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/profile-reviews")
                        .with(authentication(auditorAuth()))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}
