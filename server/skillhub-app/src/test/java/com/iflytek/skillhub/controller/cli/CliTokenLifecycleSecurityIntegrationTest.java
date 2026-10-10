package com.iflytek.skillhub.controller.cli;

import com.iflytek.skillhub.auth.entity.ApiToken;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.repository.ApiTokenRepository;
import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.cli.CliResolveResponse;
import com.iflytek.skillhub.service.cli.CliSkillAppService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CliTokenLifecycleSecurityIntegrationTest {

    private enum InvalidCredentialState {
        REVOKED,
        EXPIRED,
        UNKNOWN,
        EMPTY,
        MALFORMED
    }

    private enum EndpointCase {
        WHOAMI,
        SEARCH,
        RESOLVE,
        LATEST_DOWNLOAD,
        VERSIONED_DOWNLOAD
    }

    private enum MixedCredentialState {
        SESSION_ONLY,
        SESSION_BASIC,
        BASIC_ONLY,
        SESSION_VALID_BEARER
    }

    @Autowired MockMvc mockMvc;
    @Autowired ApiTokenService apiTokenService;
    @Autowired ApiTokenRepository apiTokenRepository;
    @Autowired UserAccountRepository userAccountRepository;
    @Autowired Clock clock;
    @MockBean CliSkillAppService cliSkillAppService;

    private String userId;
    private String sessionUserId;

    @BeforeEach
    void setUp() {
        userId = "token-matrix-" + UUID.randomUUID();
        sessionUserId = "session-matrix-" + UUID.randomUUID();
        userAccountRepository.save(new UserAccount(
                userId, "Token Matrix", userId + "@example.com", ""));
        userAccountRepository.save(new UserAccount(
                sessionUserId, "Session Matrix", sessionUserId + "@example.com", ""));
        given(cliSkillAppService.search(any(), anyInt(), any(), any()))
                .willReturn(new CliSkillAppService.CliSearchResult(List.of(), 0, 20));
        given(cliSkillAppService.resolve(anyString(), anyString(), any(), any(), any()))
                .willReturn(new CliResolveResponse(
                        "global", "demo", "1.0.0", 1L, "sha256:empty",
                        "/api/v1/skills/global/demo/versions/1.0.0/download"));
        given(cliSkillAppService.downloadLatest(anyString(), anyString(), any()))
                .willAnswer(ignored -> downloadResponse());
        given(cliSkillAppService.downloadVersion(anyString(), anyString(), anyString(), any()))
                .willAnswer(ignored -> downloadResponse());
    }

    @Test
    void whoamiWithoutAuthorizationReturns401() throws Exception {
        mockMvc.perform(get("/api/cli/v1/auth/whoami"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void whoamiWithValidPersistedTokenReturns200() throws Exception {
        String token = createActiveToken();
        mockMvc.perform(withBearer(get("/api/cli/v1/auth/whoami"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handle").value(userId));
    }

    @ParameterizedTest(name = "{0} with {1}")
    @MethodSource("mixedCredentialMatrix")
    void sessionAndAuthorizationSchemeMatrix(
            EndpointCase endpoint,
            MixedCredentialState credentialState) throws Exception {
        clearInvocations(cliSkillAppService);
        String expectedUserId = expectedUserId(credentialState);
        MockHttpServletRequestBuilder request = withCredentials(requestFor(endpoint), credentialState);

        if (endpoint == EndpointCase.WHOAMI) {
            if (credentialState == MixedCredentialState.BASIC_ONLY) {
                assertUnauthorizedEnvelope(request);
            } else {
                assertSuccessEnvelope(request)
                        .andExpect(jsonPath("$.data.handle").value(expectedUserId));
            }
            verifyNoInteractions(cliSkillAppService);
            return;
        }

        ResultActions result = mockMvc.perform(request).andExpect(status().isOk());
        if (endpoint == EndpointCase.LATEST_DOWNLOAD
                || endpoint == EndpointCase.VERSIONED_DOWNLOAD) {
            result.andExpect(content().contentType("application/zip"));
        } else {
            result.andExpect(jsonPath("$", aMapWithSize(5)))
                    .andExpect(jsonPath("$.code").value(0));
        }
        assertProjectedUser(endpoint, expectedUserId);
    }

    @Test
    void whoamiReturnsNullEmailForPersistedUserWithoutEmail() throws Exception {
        String noEmailUserId = "token-no-email-" + UUID.randomUUID();
        userAccountRepository.save(new UserAccount(noEmailUserId, "No Email User", null, ""));
        String rawToken = apiTokenService.createToken(
                noEmailUserId, "no-email-" + UUID.randomUUID(), "[\"skill:read\"]").rawToken();

        assertSuccessEnvelope(withBearer(get("/api/cli/v1/auth/whoami"), rawToken))
                .andExpect(jsonPath("$.data", hasKey("email")))
                .andExpect(jsonPath("$.data.email").value(nullValue()));
    }

    @ParameterizedTest(name = "whoami rejects {0}")
    @EnumSource(InvalidCredentialState.class)
    void whoamiRejectsInvalidBearer(InvalidCredentialState state) throws Exception {
        clearInvocations(cliSkillAppService);
        assertUnauthorizedEnvelope(withInvalidBearer(get("/api/cli/v1/auth/whoami"), state));
        verifyNoInteractions(cliSkillAppService);
    }

    @Test
    void searchWithoutAuthorizationReturns200() throws Exception {
        mockMvc.perform(get("/api/cli/v1/skills/search").param("q", "demo").param("limit", "20"))
                .andExpect(status().isOk());
    }

    @Test
    void searchWithValidPersistedTokenReturns200() throws Exception {
        String token = createActiveToken();
        mockMvc.perform(withBearer(
                        get("/api/cli/v1/skills/search").param("q", "demo").param("limit", "20"), token))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "search rejects {0}")
    @EnumSource(InvalidCredentialState.class)
    void searchRejectsInvalidBearer(InvalidCredentialState state) throws Exception {
        clearInvocations(cliSkillAppService);
        assertUnauthorizedEnvelope(withInvalidBearer(
                get("/api/cli/v1/skills/search").param("q", "demo").param("limit", "20"), state));
        verifyNoInteractions(cliSkillAppService);
    }

    @Test
    void resolveWithoutAuthorizationReturns200() throws Exception {
        mockMvc.perform(get("/api/cli/v1/skills/global/demo/resolve"))
                .andExpect(status().isOk());
    }

    @Test
    void resolveWithValidPersistedTokenReturns200() throws Exception {
        String token = createActiveToken();
        mockMvc.perform(withBearer(get("/api/cli/v1/skills/global/demo/resolve"), token))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "resolve rejects {0}")
    @EnumSource(InvalidCredentialState.class)
    void resolveRejectsInvalidBearer(InvalidCredentialState state) throws Exception {
        clearInvocations(cliSkillAppService);
        assertUnauthorizedEnvelope(withInvalidBearer(
                get("/api/cli/v1/skills/global/demo/resolve"), state));
        verifyNoInteractions(cliSkillAppService);
    }

    @Test
    void latestDownloadWithoutAuthorizationReturns200() throws Exception {
        mockMvc.perform(get("/api/cli/v1/skills/global/demo/download"))
                .andExpect(status().isOk());
    }

    @Test
    void latestDownloadWithValidPersistedTokenReturns200() throws Exception {
        String token = createActiveToken();
        mockMvc.perform(withBearer(get("/api/cli/v1/skills/global/demo/download"), token))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"));
    }

    @ParameterizedTest(name = "latest download rejects {0}")
    @EnumSource(InvalidCredentialState.class)
    void latestDownloadRejectsInvalidBearer(InvalidCredentialState state) throws Exception {
        clearInvocations(cliSkillAppService);
        assertUnauthorizedEnvelope(withInvalidBearer(
                get("/api/cli/v1/skills/global/demo/download"), state));
        verifyNoInteractions(cliSkillAppService);
    }

    @Test
    void versionedDownloadWithoutAuthorizationReturns200() throws Exception {
        mockMvc.perform(get("/api/cli/v1/skills/global/demo/versions/1.0.0/download"))
                .andExpect(status().isOk());
    }

    @Test
    void versionedDownloadWithValidPersistedTokenReturns200() throws Exception {
        String token = createActiveToken();
        mockMvc.perform(withBearer(
                        get("/api/cli/v1/skills/global/demo/versions/1.0.0/download"), token))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"));
    }

    @ParameterizedTest(name = "versioned download rejects {0}")
    @EnumSource(InvalidCredentialState.class)
    void versionedDownloadRejectsInvalidBearer(InvalidCredentialState state) throws Exception {
        clearInvocations(cliSkillAppService);
        assertUnauthorizedEnvelope(withInvalidBearer(
                get("/api/cli/v1/skills/global/demo/versions/1.0.0/download"), state));
        verifyNoInteractions(cliSkillAppService);
    }

    @Test
    void sameRawTokenIsRejectedByAllEndpointsAfterValidUseAndRevocation() throws Exception {
        ApiTokenService.TokenCreateResult token = createToken();
        String rawToken = token.rawToken();

        assertSuccessEnvelope(withBearer(get("/api/cli/v1/auth/whoami"), rawToken))
                .andExpect(jsonPath("$.data.handle").value(userId));
        assertSuccessEnvelope(withBearer(
                get("/api/cli/v1/skills/search").param("q", "demo").param("limit", "20"),
                rawToken));
        assertSuccessEnvelope(withBearer(
                get("/api/cli/v1/skills/global/demo/resolve"), rawToken));
        mockMvc.perform(withBearer(
                        get("/api/cli/v1/skills/global/demo/download"), rawToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"));
        mockMvc.perform(withBearer(
                        get("/api/cli/v1/skills/global/demo/versions/1.0.0/download"), rawToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"));

        apiTokenService.revokeToken(token.entity().getId(), userId);
        clearInvocations(cliSkillAppService);

        assertUnauthorizedEnvelope(withBearer(get("/api/cli/v1/auth/whoami"), rawToken));
        assertUnauthorizedEnvelope(withBearer(
                get("/api/cli/v1/skills/search").param("q", "demo").param("limit", "20"),
                rawToken));
        assertUnauthorizedEnvelope(withBearer(
                get("/api/cli/v1/skills/global/demo/resolve"), rawToken));
        assertUnauthorizedEnvelope(withBearer(
                get("/api/cli/v1/skills/global/demo/download"), rawToken));
        assertUnauthorizedEnvelope(withBearer(
                get("/api/cli/v1/skills/global/demo/versions/1.0.0/download"), rawToken));
        verifyNoInteractions(cliSkillAppService);
    }

    private ResultActions assertSuccessEnvelope(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(5)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").isString())
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.requestId").isString());
    }

    private void assertUnauthorizedEnvelope(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$", aMapWithSize(5)))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").isString())
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.requestId").isString());
    }

    private MockHttpServletRequestBuilder withInvalidBearer(
            MockHttpServletRequestBuilder request,
            InvalidCredentialState state) {
        return request
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader(state))
                .session(session());
    }

    private static Stream<Arguments> mixedCredentialMatrix() {
        return Stream.of(EndpointCase.values())
                .flatMap(endpoint -> Stream.of(MixedCredentialState.values())
                        .map(state -> Arguments.of(endpoint, state)));
    }

    private MockHttpServletRequestBuilder requestFor(EndpointCase endpoint) {
        return switch (endpoint) {
            case WHOAMI -> get("/api/cli/v1/auth/whoami");
            case SEARCH -> get("/api/cli/v1/skills/search")
                    .param("q", "demo")
                    .param("limit", "20");
            case RESOLVE -> get("/api/cli/v1/skills/global/demo/resolve");
            case LATEST_DOWNLOAD -> get("/api/cli/v1/skills/global/demo/download");
            case VERSIONED_DOWNLOAD ->
                    get("/api/cli/v1/skills/global/demo/versions/1.0.0/download");
        };
    }

    private MockHttpServletRequestBuilder withCredentials(
            MockHttpServletRequestBuilder request,
            MixedCredentialState state) {
        return switch (state) {
            case SESSION_ONLY -> request.session(session());
            case SESSION_BASIC -> request.session(session())
                    .header(HttpHeaders.AUTHORIZATION, "Basic dGVzdDp0ZXN0");
            case BASIC_ONLY -> request.header(HttpHeaders.AUTHORIZATION, "Basic dGVzdDp0ZXN0");
            case SESSION_VALID_BEARER -> withBearer(request.session(session()), createActiveToken());
        };
    }

    private String expectedUserId(MixedCredentialState state) {
        return switch (state) {
            case SESSION_ONLY, SESSION_BASIC -> sessionUserId;
            case BASIC_ONLY -> null;
            case SESSION_VALID_BEARER -> userId;
        };
    }

    private void assertProjectedUser(EndpointCase endpoint, String expectedUserId) {
        if (endpoint == EndpointCase.SEARCH) {
            ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
            verify(cliSkillAppService).search(any(), anyInt(), userCaptor.capture(), any());
            assertEquals(expectedUserId, userCaptor.getValue());
            return;
        }
        if (endpoint == EndpointCase.RESOLVE) {
            ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
            verify(cliSkillAppService).resolve(anyString(), anyString(), any(), userCaptor.capture(), any());
            assertEquals(expectedUserId, userCaptor.getValue());
            return;
        }

        ArgumentCaptor<HttpServletRequest> requestCaptor = ArgumentCaptor.forClass(HttpServletRequest.class);
        if (endpoint == EndpointCase.LATEST_DOWNLOAD) {
            verify(cliSkillAppService).downloadLatest(anyString(), anyString(), requestCaptor.capture());
        } else {
            verify(cliSkillAppService).downloadVersion(
                    anyString(), anyString(), anyString(), requestCaptor.capture());
        }
        assertEquals(expectedUserId, requestCaptor.getValue().getAttribute("userId"));
    }

    private MockHttpServletRequestBuilder withBearer(
            MockHttpServletRequestBuilder request,
            String rawToken) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + rawToken);
    }

    private String authorizationHeader(InvalidCredentialState state) {
        return switch (state) {
            case REVOKED -> {
                ApiTokenService.TokenCreateResult result = createToken();
                apiTokenService.revokeToken(result.entity().getId(), userId);
                yield "Bearer " + result.rawToken();
            }
            case EXPIRED -> {
                ApiTokenService.TokenCreateResult result = createToken();
                ApiToken token = result.entity();
                token.setExpiresAt(Instant.now(clock).minusSeconds(1));
                apiTokenRepository.saveAndFlush(token);
                yield "Bearer " + result.rawToken();
            }
            case UNKNOWN -> "Bearer sk_unknown_" + UUID.randomUUID();
            case EMPTY -> "Bearer ";
            case MALFORMED -> "Bearer";
        };
    }

    private String createActiveToken() {
        return createToken().rawToken();
    }

    private ApiTokenService.TokenCreateResult createToken() {
        return apiTokenService.createToken(
                userId, "matrix-" + UUID.randomUUID(), "[\"skill:read\"]");
    }

    private MockHttpSession session() {
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(sessionAuthentication());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                securityContext);
        return session;
    }

    private UsernamePasswordAuthenticationToken sessionAuthentication() {
        PlatformPrincipal principal = new PlatformPrincipal(
                sessionUserId,
                "Session User",
                sessionUserId + "@example.com",
                "",
                "session",
                Set.of("USER"));
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    private ResponseEntity<InputStreamResource> downloadResponse() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(new InputStreamResource(
                        new ByteArrayInputStream("zip".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }
}
