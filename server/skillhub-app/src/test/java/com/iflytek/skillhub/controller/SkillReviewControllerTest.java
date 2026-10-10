package com.iflytek.skillhub.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.SkillReviewMeResponse;
import com.iflytek.skillhub.dto.SkillReviewResponse;
import com.iflytek.skillhub.service.SkillReviewAppService;
import java.util.List;
import java.util.Set;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SkillReviewControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private SkillReviewAppService reviewAppService;
    @MockBean private NamespaceMemberRepository namespaceMemberRepository;

    @Test
    void publicReviewListIsAnonymous() throws Exception {
        when(reviewAppService.list(eq(10L), eq(null), any(), eq(Set.of()), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(List.of(), 0, 0, 20));

        mockMvc.perform(get("/api/v1/skills/10/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void publicReviewListOmitsInternalUserAndModerationFields() throws Exception {
        SkillReviewResponse review = new SkillReviewResponse(
                8L, null, "Alice", null, (short) 5, "Useful", "VISIBLE", false,
                null, null, null);
        when(reviewAppService.list(eq(10L), eq(null), any(), eq(Set.of()), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(List.of(review), 1, 0, 20));

        mockMvc.perform(get("/api/v1/skills/10/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].displayName").value("Alice"))
                .andExpect(jsonPath("$.data.items[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].moderationReason").doesNotExist());
    }

    @Test
    void currentUserReviewRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/skills/10/reviews/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void anonymousUserCannotUpsertOrClearReview() throws Exception {
        mockMvc.perform(put("/api/v1/skills/10/reviews/me")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":4,\"reviewText\":\"Useful\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(delete("/api/v1/skills/10/reviews/me")
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void authenticatedUserCanUpsertReview() throws Exception {
        var principal = principal("user-42", Set.of());
        when(reviewAppService.upsert(eq(10L), eq("user-42"), eq((short) 4), eq("Useful"), any(), eq(Set.of())))
                .thenReturn(new SkillReviewMeResponse(
                        true, (short) 4, true, 8L, "Useful", "VISIBLE", null, null, null));

        mockMvc.perform(put("/api/v1/skills/10/reviews/me")
                        .with(authentication(authToken(principal, "ROLE_USER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":4,\"reviewText\":\"Useful\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewed").value(true))
                .andExpect(jsonPath("$.data.reviewText").value("Useful"));

        verify(reviewAppService).upsert(eq(10L), eq("user-42"), eq((short) 4), eq("Useful"), any(), eq(Set.of()));
    }

    @Test
    void reviewScoreIsRequiredByTheApiContract() throws Exception {
        var principal = principal("user-42", Set.of());

        mockMvc.perform(put("/api/v1/skills/10/reviews/me")
                        .with(authentication(authToken(principal, "ROLE_USER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewText\":\"Useful\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void reviewTextIsRequiredByTheApiContract() throws Exception {
        var principal = principal("user-42", Set.of());

        mockMvc.perform(put("/api/v1/skills/10/reviews/me")
                        .with(authentication(authToken(principal, "ROLE_USER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":4}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void skillAdminCanHideReview() throws Exception {
        var principal = principal("admin", Set.of("SKILL_ADMIN"));

        mockMvc.perform(post("/api/v1/admin/skill-reviews/8/hide")
                        .with(authentication(authToken(principal, "ROLE_SKILL_ADMIN")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"spam\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(reviewAppService).hide(eq(8L), eq("admin"), eq("spam"), any());
    }

    @Test
    void ordinaryUserCannotHideReview() throws Exception {
        var principal = principal("user-42", Set.of());

        mockMvc.perform(post("/api/v1/admin/skill-reviews/8/hide")
                        .with(authentication(authToken(principal, "ROLE_USER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void moderationReasonRejectsMoreThanFiveHundredCharacters() throws Exception {
        var principal = principal("admin", Set.of("SKILL_ADMIN"));

        mockMvc.perform(post("/api/v1/admin/skill-reviews/8/hide")
                        .with(authentication(authToken(principal, "ROLE_SKILL_ADMIN")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    private PlatformPrincipal principal(String userId, Set<String> roles) {
        return new PlatformPrincipal(userId, userId, userId + "@example.test", null, "local", roles);
    }

    private UsernamePasswordAuthenticationToken authToken(PlatformPrincipal principal, String role) {
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority(role))
        );
    }
}
