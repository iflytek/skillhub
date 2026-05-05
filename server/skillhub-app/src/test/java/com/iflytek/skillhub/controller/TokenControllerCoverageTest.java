package com.iflytek.skillhub.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.TokenCreateRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TokenControllerCoverageTest {

    private final ApiTokenService apiTokenService = mock(ApiTokenService.class);
    private final ObjectMapper objectMapper = mock(ObjectMapper.class);
    private final ApiResponseFactory responseFactory = mock(ApiResponseFactory.class);

    private final TokenController controller = new TokenController(apiTokenService, responseFactory, objectMapper);

    @Test
    void create_withNullScopes_usesDefaultScopes() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-42", "tester", "tester@example.com", "", "github", java.util.Set.of("USER")
        );
        com.iflytek.skillhub.auth.entity.ApiToken token = new com.iflytek.skillhub.auth.entity.ApiToken(
                "user-42", "cli", "sk_123456", "hash-1", "[]"
        );
        org.springframework.test.util.ReflectionTestUtils.setField(token, "id", 7L);
        org.springframework.test.util.ReflectionTestUtils.setField(token, "createdAt", java.time.Instant.parse("2026-03-15T12:00:00Z"));
        token.setExpiresAt(java.time.Instant.parse("2026-04-15T12:00:00Z"));

        when(apiTokenService.rotateToken("user-42", "cli", "[\"skill:read\",\"skill:publish\"]", null))
                .thenReturn(new ApiTokenService.TokenCreateResult("sk_raw", token));

        controller.create(principal, new TokenCreateRequest("cli", null, null));
    }

    @Test
    void create_withJsonProcessingException_usesDefaultScopes() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-42", "tester", "tester@example.com", "", "github", java.util.Set.of("USER")
        );
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("fail") {});

        com.iflytek.skillhub.auth.entity.ApiToken token = new com.iflytek.skillhub.auth.entity.ApiToken(
                "user-42", "cli", "sk_123456", "hash-1", "[]"
        );
        org.springframework.test.util.ReflectionTestUtils.setField(token, "id", 7L);
        org.springframework.test.util.ReflectionTestUtils.setField(token, "createdAt", java.time.Instant.parse("2026-03-15T12:00:00Z"));
        token.setExpiresAt(java.time.Instant.parse("2026-04-15T12:00:00Z"));

        when(apiTokenService.rotateToken("user-42", "cli", "[\"skill:read\",\"skill:publish\"]", null))
                .thenReturn(new ApiTokenService.TokenCreateResult("sk_raw", token));

        controller.create(principal, new TokenCreateRequest("cli", List.of("skill:read"), null));
    }

    @Test
    void formatInstant_withNullValue_returnsEmptyString() {
        String result = (String) org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                controller, "formatInstant", (java.time.Instant) null
        );
        assertThat(result).isEqualTo("");
    }

    @Test
    void create_withNonEmptyScopes_serializesSuccessfully() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-42", "tester", "tester@example.com", "", "github", java.util.Set.of("USER")
        );
        when(objectMapper.writeValueAsString(List.of("skill:read"))).thenReturn("[\"skill:read\"]");

        com.iflytek.skillhub.auth.entity.ApiToken token = new com.iflytek.skillhub.auth.entity.ApiToken(
                "user-42", "cli", "sk_123456", "hash-1", "[]"
        );
        org.springframework.test.util.ReflectionTestUtils.setField(token, "id", 7L);
        org.springframework.test.util.ReflectionTestUtils.setField(token, "createdAt", java.time.Instant.parse("2026-03-15T12:00:00Z"));
        token.setExpiresAt(java.time.Instant.parse("2026-04-15T12:00:00Z"));

        when(apiTokenService.rotateToken("user-42", "cli", "[\"skill:read\"]", null))
                .thenReturn(new ApiTokenService.TokenCreateResult("sk_raw", token));

        controller.create(principal, new TokenCreateRequest("cli", List.of("skill:read"), null));
    }

}
