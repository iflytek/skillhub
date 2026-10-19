package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.compat.ClawHubCompatAppService;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.SkillAvatarUploadResponse;
import com.iflytek.skillhub.service.SkillAvatarUploadService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises the production ordered chains, stored sessions, CSRF and token route policy. */
@SpringBootTest(properties = "skillhub.ratelimit.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SkillAvatarUploadSecurityIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired UserAccountRepository users;
    @MockBean ApiTokenService tokens;
    @MockBean SkillAvatarUploadService uploads;
    @MockBean ClawHubCompatAppService compatibility;
    private String userId;

    @BeforeEach
    void setUp() {
        userId = "avatar-security-" + UUID.randomUUID();
        users.save(new UserAccount(userId, "Avatar user", userId + "@example.test", ""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1", "/api/web"})
    void browserSessionWithCsrfCanUpload(String prefix) throws Exception {
        given(uploads.upload(eq(userId), any(byte[].class))).willReturn(new SkillAvatarUploadResponse(
                "upload-1", prefix + "/skills/avatar-uploads/upload-1/image", Instant.now().plusSeconds(600)));
        mockMvc.perform(multipart(prefix + "/skills/avatar-uploads").file(image())
                        .session(session()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadId").value("upload-1"));
        verify(uploads).upload(eq(userId), any(byte[].class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1", "/api/web"})
    void browserCookieToHeaderCsrfCanUpload(String prefix) throws Exception {
        var browserSession = session();
        String csrfToken = UUID.randomUUID().toString();
        given(uploads.upload(eq(userId), any(byte[].class))).willReturn(new SkillAvatarUploadResponse(
                "upload-cookie", prefix + "/skills/avatar-uploads/upload-cookie/image", Instant.now().plusSeconds(600)));
        mockMvc.perform(multipart(prefix + "/skills/avatar-uploads").file(image()).session(browserSession)
                        .cookie(new Cookie("SESSION", browserSession.getId()), new Cookie("XSRF-TOKEN", csrfToken))
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadId").value("upload-cookie"));
        verify(uploads).upload(eq(userId), any(byte[].class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1", "/api/web"})
    void browserSessionWithoutCsrfHeaderCannotUpload(String prefix) throws Exception {
        mockMvc.perform(multipart(prefix + "/skills/avatar-uploads").file(image()).session(session())
                        .cookie(new Cookie("XSRF-TOKEN", UUID.randomUUID().toString())))
                .andExpect(status().isForbidden());
        verifyNoInteractions(uploads);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1", "/api/web"})
    void browserSessionWithoutCsrfCookieCannotUpload(String prefix) throws Exception {
        mockMvc.perform(multipart(prefix + "/skills/avatar-uploads").file(image()).session(session()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.msg").value("Session expired"));
        verifyNoInteractions(uploads);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1", "/api/web"})
    void browserSessionWithInvalidCsrfCannotUpload(String prefix) throws Exception {
        mockMvc.perform(multipart(prefix + "/skills/avatar-uploads").file(image())
                        .session(session()).with(invalidCsrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(uploads);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1", "/api/web"})
    void anonymousWithCsrfCannotUpload(String prefix) throws Exception {
        mockMvc.perform(multipart(prefix + "/skills/avatar-uploads").file(image()).with(csrf()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(uploads);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1", "/api/web"})
    void publishTokenWithCsrfCannotUseSessionOnlyUpload(String prefix) throws Exception {
        String rawToken = "avatar-test-" + UUID.randomUUID();
        var token = new com.iflytek.skillhub.auth.entity.ApiToken(userId, "avatar", "avatar", "test-hash",
                "[\"skill:publish\"]");
        given(tokens.validateToken(rawToken)).willReturn(java.util.Optional.of(token));
        mockMvc.perform(multipart(prefix + "/skills/avatar-uploads").file(image()).with(csrf())
                        .header("Authorization", "Bearer " + rawToken))
                .andExpect(status().isForbidden());
        verifyNoInteractions(uploads);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1", "/api/web"})
    void publishTokenWithoutCsrfCannotUseSessionOnlyUpload(String prefix) throws Exception {
        String rawToken = "avatar-test-" + UUID.randomUUID();
        var token = new com.iflytek.skillhub.auth.entity.ApiToken(userId, "avatar", "avatar", "test-hash",
                "[\"skill:publish\"]");
        given(tokens.validateToken(rawToken)).willReturn(java.util.Optional.of(token));
        mockMvc.perform(multipart(prefix + "/skills/avatar-uploads").file(image())
                        .header("Authorization", "Bearer " + rawToken))
                .andExpect(status().isForbidden());
        verifyNoInteractions(uploads);
    }

    @Test
    void sameNamedGetSlugStillUsesCompatibilityEndpoint() throws Exception {
        given(compatibility.getSkill(eq("avatar-uploads"), isNull(), isNull()))
                .willThrow(new DomainNotFoundException("error.skill.notFound", "avatar-uploads"));
        mockMvc.perform(get("/api/v1/skills/avatar-uploads")).andExpect(status().isNotFound());
        verify(compatibility).getSkill(eq("avatar-uploads"), isNull(), isNull());
        verifyNoInteractions(uploads);
    }

    @Test
    void unmappedApiReturnsNotFoundEnvelopeWithoutResourceDetails() throws Exception {
        mockMvc.perform(get("/api/v1/release-validation/internal-path-secret").session(session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value("Resource not found"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(content().string(not(containsString("internal-path-secret"))))
                .andExpect(content().string(not(containsString("NoResourceFoundException"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"zh", "ru"})
    void unmappedApiNotFoundIsLocalized(String language) throws Exception {
        mockMvc.perform(get("/api/v1/release-validation/internal-path-secret").session(session())
                        .header("Accept-Language", language))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value("zh".equals(language) ? "未找到资源" : "Ресурс не найден"))
                .andExpect(content().string(not(containsString("internal-path-secret"))));
    }

    @Test
    void realInternalFailureRemains500WithoutExceptionDetails() throws Exception {
        given(uploads.upload(eq(userId), any(byte[].class)))
                .willThrow(new RuntimeException("database-password-secret"));
        mockMvc.perform(multipart("/api/web/skills/avatar-uploads").file(image())
                        .session(session()).with(csrf()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("database-password-secret"))));
    }

    private MockHttpSession session() {
        var context = SecurityContextHolder.createEmptyContext();
        var principal = new PlatformPrincipal(userId, "Avatar user", userId + "@example.test", "", "local", Set.of());
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private MockMultipartFile image() {
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
        return new MockMultipartFile("file", "avatar.png", "image/png", png);
    }

    // Use the production cookie-to-header protocol. SecurityMockMvc's csrf() replaces the
    // filter's token repository, which would stop these tests from exercising real cookies.
    private RequestPostProcessor csrf() {
        return request -> {
            String token = UUID.randomUUID().toString();
            request.setCookies(new Cookie("XSRF-TOKEN", token));
            request.addHeader("X-XSRF-TOKEN", token);
            return request;
        };
    }

    private RequestPostProcessor invalidCsrf() {
        return request -> {
            request.setCookies(new Cookie("XSRF-TOKEN", UUID.randomUUID().toString()));
            request.addHeader("X-XSRF-TOKEN", "invalid-csrf-token");
            return request;
        };
    }
}
