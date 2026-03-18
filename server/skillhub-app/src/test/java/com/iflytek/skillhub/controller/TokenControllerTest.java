package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class TokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @MockBean
    private ApiTokenService apiTokenService;

    @Test
    void revoke_returns204NoContent() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-42", "tester", "tester@example.com", "", "github", Set.of("USER")
        );
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        mockMvc.perform(delete("/api/v1/tokens/7")
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(apiTokenService).revokeToken(7L, "user-42");
    }

    @Test
    void create_rejectsNamesLongerThan64Characters() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-42", "tester", "tester@example.com", "", "github", Set.of("USER")
        );
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        given(apiTokenService.rotateToken(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.nullable(String.class)))
                .willThrow(new DomainBadRequestException("validation.token.name.size"));

        mockMvc.perform(post("/api/v1/tokens")
                        .with(authentication(auth))
                        .with(csrf())
                        .header("Accept-Language", "zh-CN")
                        .contentType("application/json")
                        .content("""
                                {"name":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void create_rejectsDuplicateActiveNames() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-42", "tester", "tester@example.com", "", "github", Set.of("USER")
        );
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        given(apiTokenService.rotateToken(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.nullable(String.class)))
                .willThrow(new DomainBadRequestException("error.token.name.duplicate"));

        mockMvc.perform(post("/api/v1/tokens")
                        .with(authentication(auth))
                        .with(csrf())
                        .header("Accept-Language", "zh-CN")
                        .contentType("application/json")
                        .content("""
                                {"name":"cli"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void create_passesExpirationToService() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-42", "tester", "tester@example.com", "", "github", Set.of("USER")
        );
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        var token = new com.iflytek.skillhub.auth.entity.ApiToken("user-42", "cli", "sk_123456", "hash-1", "[]");
        org.springframework.test.util.ReflectionTestUtils.setField(token, "id", 7L);
        org.springframework.test.util.ReflectionTestUtils.setField(token, "createdAt", java.time.Instant.parse("2026-03-15T12:00:00Z"));
        token.setExpiresAt(java.time.Instant.parse("2026-04-15T12:00:00Z"));

        given(apiTokenService.rotateToken("user-42", "cli", "[\"skill:read\",\"skill:publish\"]", "2026-04-15T12:00:00"))
                .willReturn(new ApiTokenService.TokenCreateResult("sk_raw", token));

        mockMvc.perform(post("/api/v1/tokens")
                        .with(authentication(auth))
                        .with(csrf())
                        .contentType("application/json")
                .content("""
                                {"name":"cli","expiresAt":"2026-04-15T12:00:00"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-04-15T12:00:00Z"));
    }

    @Test
    void list_returns_paginated_tokens() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-42", "tester", "tester@example.com", "", "github", Set.of("USER")
        );
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        var tokenPage = new PageImpl<>(
                List.of(
                        new com.iflytek.skillhub.auth.entity.ApiToken("user-42", "cli", "sk_123456", "hash-1", "[]"),
                        new com.iflytek.skillhub.auth.entity.ApiToken("user-42", "deploy", "sk_654321", "hash-2", "[]")
                ),
                PageRequest.of(1, 10),
                12
        );
        var first = tokenPage.getContent().get(0);
        var second = tokenPage.getContent().get(1);
        org.springframework.test.util.ReflectionTestUtils.setField(first, "id", 7L);
        org.springframework.test.util.ReflectionTestUtils.setField(first, "createdAt", java.time.Instant.parse("2026-03-14T10:00:00Z"));
        org.springframework.test.util.ReflectionTestUtils.setField(second, "id", 8L);
        org.springframework.test.util.ReflectionTestUtils.setField(second, "createdAt", java.time.Instant.parse("2026-03-14T11:00:00Z"));

        given(apiTokenService.listActiveTokens("user-42", 1, 10)).willReturn(tokenPage);

        mockMvc.perform(get("/api/v1/tokens")
                        .with(authentication(auth))
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].name").value("cli"))
                .andExpect(jsonPath("$.data.items[1].name").value("deploy"))
                .andExpect(jsonPath("$.data.total").value(12))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(10));
    }

    @Test
    void updateExpiration_returnsUpdatedToken() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-42", "tester", "tester@example.com", "", "github", Set.of("USER")
        );
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        var token = new com.iflytek.skillhub.auth.entity.ApiToken("user-42", "cli", "sk_123456", "hash-1", "[]");
        org.springframework.test.util.ReflectionTestUtils.setField(token, "id", 7L);
        org.springframework.test.util.ReflectionTestUtils.setField(token, "createdAt", java.time.Instant.parse("2026-03-14T10:00:00Z"));
        token.setExpiresAt(java.time.Instant.parse("2026-05-01T09:30:00Z"));

        given(apiTokenService.updateExpiration(7L, "user-42", "2026-05-01T09:30"))
                .willReturn(token);

        mockMvc.perform(put("/api/v1/tokens/7/expiration")
                        .with(authentication(auth))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"expiresAt":"2026-05-01T09:30"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-05-01T09:30:00Z"));
    }

    @Test
    void list_returnsUtcTimestamps_evenWhenJvmDefaultTimeZoneChanges() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-42", "tester", "tester@example.com", "", "github", Set.of("USER")
        );
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        var tokenPage = new PageImpl<>(
                List.of(new com.iflytek.skillhub.auth.entity.ApiToken("user-42", "cli", "sk_123456", "hash-1", "[]")),
                PageRequest.of(0, 10),
                1
        );
        var token = tokenPage.getContent().getFirst();
        org.springframework.test.util.ReflectionTestUtils.setField(token, "id", 7L);
        org.springframework.test.util.ReflectionTestUtils.setField(token, "createdAt", java.time.Instant.parse("2026-03-14T10:00:00Z"));
        token.setExpiresAt(java.time.Instant.parse("2026-05-01T09:30:00Z"));

        given(apiTokenService.listActiveTokens("user-42", 0, 10)).willReturn(tokenPage);

        TimeZone original = TimeZone.getDefault();
        try {
            for (String zoneId : List.of("Asia/Shanghai", "America/Los_Angeles")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
                mockMvc.perform(get("/api/v1/tokens")
                                .with(authentication(auth))
                                .param("page", "0")
                                .param("size", "10"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.items[0].createdAt").value("2026-03-14T10:00:00Z"))
                        .andExpect(jsonPath("$.data.items[0].expiresAt").value("2026-05-01T09:30:00Z"));
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
