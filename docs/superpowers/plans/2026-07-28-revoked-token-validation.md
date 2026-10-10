# Revoked API Token Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lock the CLI API's fail-closed Bearer behavior and Web Session fallback with persisted lifecycle and mixed-credential tests, prove 401/403 semantics on every affected read endpoint, publish the authentication OpenAPI contract, and reconcile source behavior with the actual runtime artifact.

**Architecture:** Keep `ApiTokenAuthenticationFilter` as the sole Bearer authentication entry point while preserving Spring Security's existing Web Session identity. Valid Bearer replaces Session; invalid Bearer fails closed without Session fallback; absent or non-Bearer Authorization preserves Session and otherwise leaves public reads anonymous. Use one Spring Boot/MockMvc class with real token and user persistence plus deterministic controller-service stubs for the credential-state matrix, and a second Spring Boot/MockMvc class with real query/download authorization plus persisted PRIVATE and matching PUBLIC skills for authorization checks. Production authentication code remains unchanged unless the unmodified-source matrix reproduces a failure; any such failure stops this plan for systematic root-cause analysis before a minimal fix is planned.

**Tech Stack:** Java 21, Spring Boot 3.2, Spring Security, Spring Data JPA/H2, MockMvc, JUnit 5 parameterized tests, Mockito, OpenAPI 3.0 YAML, Docker/OCI image inspection.

---

## File Map

- Create `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/cli/CliTokenLifecycleSecurityIntegrationTest.java`: persisted valid/revoked/expired/unknown/empty/malformed credential matrix for each CLI endpoint.
- Create `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/cli/CliRestrictedReadAuthorizationIntegrationTest.java`: real PRIVATE-skill search omission and read authorization through resolve, latest download, and versioned download.
- Modify `docs/03-authentication-design.md`: current CLI route table, Web Session/Bearer priority, and explicit anonymous/401/403 rules.
- Create `docs/api/authentication.openapi.yaml`: OpenAPI 3.0 contract for whoami, search, resolve, latest download, and versioned download.
- Do not modify `server/skillhub-auth/src/main/**` unless Task 5 records a failing unmodified-source assertion and a separate systematic-debugging plan amendment identifies the root cause.

### Task 1: Persisted credential fixture and whoami/search/resolve matrix

**Files:**
- Create: `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/cli/CliTokenLifecycleSecurityIntegrationTest.java`

- [ ] **Step 1: Create the integration test fixture and endpoint tests**

Create the class with real `ApiTokenService`, `ApiTokenRepository`, and `UserAccountRepository`; mock only `CliSkillAppService` so successful public reads are deterministic. Add independent anonymous, valid, and parameterized invalid-state methods for whoami, search, and resolve:

```java
package com.iflytek.skillhub.controller.cli;

import com.iflytek.skillhub.auth.entity.ApiToken;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.repository.ApiTokenRepository;
import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.cli.CliResolveResponse;
import com.iflytek.skillhub.service.cli.CliSkillAppService;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Autowired MockMvc mockMvc;
    @Autowired ApiTokenService apiTokenService;
    @Autowired ApiTokenRepository apiTokenRepository;
    @Autowired UserAccountRepository userAccountRepository;
    @Autowired Clock clock;
    @MockBean CliSkillAppService cliSkillAppService;

    private String userId;

    @BeforeEach
    void setUp() {
        userId = "token-matrix-" + UUID.randomUUID();
        userAccountRepository.save(new UserAccount(
                userId, "Token Matrix", userId + "@example.com", ""));
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

    @ParameterizedTest(name = "whoami rejects {0}")
    @EnumSource(InvalidCredentialState.class)
    void whoamiRejectsInvalidBearer(InvalidCredentialState state) throws Exception {
        clearInvocations(cliSkillAppService);
        mockMvc.perform(withInvalidBearer(get("/api/cli/v1/auth/whoami"), state))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
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
        mockMvc.perform(withInvalidBearer(
                        get("/api/cli/v1/skills/search").param("q", "demo").param("limit", "20"), state))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
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
        mockMvc.perform(withInvalidBearer(get("/api/cli/v1/skills/global/demo/resolve"), state))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(cliSkillAppService);
    }

    private MockHttpServletRequestBuilder withInvalidBearer(
            MockHttpServletRequestBuilder request,
            InvalidCredentialState state) {
        return request
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader(state))
                .with(authentication(sessionAuthentication()));
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

    private UsernamePasswordAuthenticationToken sessionAuthentication() {
        PlatformPrincipal principal = new PlatformPrincipal(
                userId, "Session User", userId + "@example.com", "", "session", Set.of("USER"));
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    private ResponseEntity<InputStreamResource> downloadResponse() {
        return ResponseEntity.ok(new InputStreamResource(
                new ByteArrayInputStream("zip".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }
}
```

- [ ] **Step 2: Apply a reversible fail-open mutation before the first test run**

Temporarily change both rejection branches in `ApiTokenAuthenticationFilter.doFilterInternal` so malformed and invalid credentials continue down the chain. Do not stage or commit this mutation:

```java
if (rawToken == null) {
    filterChain.doFilter(request, response);
    return;
}

var token = apiTokenService.validateToken(rawToken);
if (token.isEmpty()) {
    filterChain.doFilter(request, response);
    return;
}
```

- [ ] **Step 3: Run whoami RED verification**

Run:

```bash
cd server && ./mvnw -pl skillhub-app -am \
  -Dtest=CliTokenLifecycleSecurityIntegrationTest#whoamiRejectsInvalidBearer \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL for the invalid-state invocations because the pre-authenticated session reaches whoami and returns 200 instead of 401.

- [ ] **Step 4: Run search RED verification**

Run the same Maven command with `#searchRejectsInvalidBearer`.

Expected: FAIL with expected 401 but actual 200 for revoked, expired, unknown, empty, and malformed Bearer credentials.

- [ ] **Step 5: Run resolve RED verification**

Run the same Maven command with `#resolveRejectsInvalidBearer`.

Expected: FAIL with expected 401 but actual 200 for all invalid credential states.

- [ ] **Step 6: Restore the two original reject branches**

Restore exactly:

```java
if (rawToken == null) {
    rejectBearer(request, response);
    return;
}

var token = apiTokenService.validateToken(rawToken);
if (token.isEmpty()) {
    rejectBearer(request, response);
    return;
}
```

Confirm `git diff -- server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/token/ApiTokenAuthenticationFilter.java` is empty.

- [ ] **Step 7: Run whoami/search/resolve GREEN commands independently**

Run three Maven commands, one for each of:

```text
CliTokenLifecycleSecurityIntegrationTest#whoamiRejectsInvalidBearer
CliTokenLifecycleSecurityIntegrationTest#searchRejectsInvalidBearer
CliTokenLifecycleSecurityIntegrationTest#resolveRejectsInvalidBearer
```

Expected: each command reports all parameterized invocations PASS, with no production authentication diff.

### Task 2: Latest download matrix

**Files:**
- Modify: `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/cli/CliTokenLifecycleSecurityIntegrationTest.java`

- [ ] **Step 1: Add independent latest-download methods**

Insert before the helper methods:

```java
@Test
void latestDownloadWithoutAuthorizationReturns200() throws Exception {
    mockMvc.perform(get("/api/cli/v1/skills/global/demo/download"))
            .andExpect(status().isOk());
}

@Test
void latestDownloadWithValidPersistedTokenReturns200() throws Exception {
    String token = createActiveToken();
    mockMvc.perform(withBearer(get("/api/cli/v1/skills/global/demo/download"), token))
            .andExpect(status().isOk());
}

@ParameterizedTest(name = "latest download rejects {0}")
@EnumSource(InvalidCredentialState.class)
void latestDownloadRejectsInvalidBearer(InvalidCredentialState state) throws Exception {
    clearInvocations(cliSkillAppService);
    mockMvc.perform(withInvalidBearer(get("/api/cli/v1/skills/global/demo/download"), state))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401));
    verifyNoInteractions(cliSkillAppService);
}
```

- [ ] **Step 2: Reapply the reversible fail-open mutation and run latest-download RED**

Run:

```bash
cd server && ./mvnw -pl skillhub-app -am \
  -Dtest=CliTokenLifecycleSecurityIntegrationTest#latestDownloadRejectsInvalidBearer \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL with expected 401 but actual 200 for every invalid state.

- [ ] **Step 3: Restore the original reject branches and run latest-download GREEN**

Run the same command after restoring the filter.

Expected: all five invalid-state invocations PASS. Then run independent anonymous and valid methods with `#latestDownloadWithoutAuthorizationReturns200` and `#latestDownloadWithValidPersistedTokenReturns200`; both PASS.

### Task 3: Versioned download matrix

**Files:**
- Modify: `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/cli/CliTokenLifecycleSecurityIntegrationTest.java`

- [ ] **Step 1: Add independent versioned-download methods**

Insert before the helper methods:

```java
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
            .andExpect(status().isOk());
}

@ParameterizedTest(name = "versioned download rejects {0}")
@EnumSource(InvalidCredentialState.class)
void versionedDownloadRejectsInvalidBearer(InvalidCredentialState state) throws Exception {
    clearInvocations(cliSkillAppService);
    mockMvc.perform(withInvalidBearer(
                    get("/api/cli/v1/skills/global/demo/versions/1.0.0/download"), state))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401));
    verifyNoInteractions(cliSkillAppService);
}
```

- [ ] **Step 2: Reapply the reversible fail-open mutation and run versioned-download RED**

Run the focused method command for `#versionedDownloadRejectsInvalidBearer`.

Expected: FAIL with expected 401 but actual 200 for every invalid state.

- [ ] **Step 3: Restore the filter and run versioned-download GREEN independently**

Run focused commands for the invalid, anonymous, and valid versioned-download methods.

Expected: all commands PASS and the filter source has no diff.

- [ ] **Step 4: Add and prove the same-token valid-to-revoked replay**

Create one token through `ApiTokenService`, retain its raw value, and use that
same value successfully against whoami, search, resolve, latest download, and
versioned download. Revoke the persisted token through
`ApiTokenService.revokeToken`, clear prior business-service invocations, then
replay the exact same raw value against all five endpoints. Each replay must
return 401 and the mocked business service must receive no post-revocation
interaction.

For the three valid JSON responses and all five revoked error responses, assert
that the outer JSON object contains exactly `code`, `msg`, `data`, `timestamp`,
and `requestId`; successful downloads remain binary-stream exceptions.

Apply the reversible invalid-token fail-open mutation and run:

```bash
cd server && ./mvnw -pl skillhub-app -am \
  -Dtest=CliTokenLifecycleSecurityIntegrationTest#sameRawTokenIsRejectedByAllEndpointsAfterValidUseAndRevocation \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected RED: at least one public read replay returns 200 instead of 401.
Restore the filter, confirm its production diff is empty, and rerun the same
command. Expected GREEN: one test passes with all five valid calls and all five
revoked replays exercised.

- [ ] **Step 5: Run the complete persisted credential matrix**

Run:

```bash
cd server && ./mvnw -pl skillhub-app -am \
  -Dtest=CliTokenLifecycleSecurityIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS for all five endpoints and all absent, valid, revoked, expired,
unknown, empty, and malformed credential cases, plus the same-token lifecycle
replay.

- [ ] **Step 6: Commit the credential matrix**

```bash
git add server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/cli/CliTokenLifecycleSecurityIntegrationTest.java
git commit -s -m "test(auth): cover persisted CLI token states (#605)"
```

### Task 4: Real restricted-read 403 boundary

**Files:**
- Create: `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/cli/CliRestrictedReadAuthorizationIntegrationTest.java`

- [ ] **Step 1: Create a persisted PRIVATE skill fixture and real authorization tests**

```java
package com.iflytek.skillhub.controller.cli;

import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CliRestrictedReadAuthorizationIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ApiTokenService apiTokenService;
    @Autowired UserAccountRepository userAccountRepository;
    @Autowired NamespaceRepository namespaceRepository;
    @Autowired SkillRepository skillRepository;
    @Autowired SkillVersionRepository skillVersionRepository;

    private String namespaceSlug;
    private String skillSlug;
    private String version;
    private String ownerToken;
    private String outsiderToken;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String ownerId = "private-owner-" + suffix;
        String outsiderId = "private-outsider-" + suffix;
        namespaceSlug = "private-ns-" + suffix;
        skillSlug = "private-skill-" + suffix;
        version = "1.0.0";

        userAccountRepository.save(new UserAccount(ownerId, "Owner", ownerId + "@example.com", ""));
        userAccountRepository.save(new UserAccount(
                outsiderId, "Outsider", outsiderId + "@example.com", ""));
        ownerToken = apiTokenService.createToken(
                ownerId, "owner-token-" + suffix, "[\"skill:read\"]").rawToken();
        outsiderToken = apiTokenService.createToken(
                outsiderId, "outsider-token-" + suffix, "[\"skill:read\"]").rawToken();

        Namespace namespace = namespaceRepository.save(new Namespace(namespaceSlug, "Private NS", ownerId));
        Skill skill = skillRepository.save(new Skill(
                namespace.getId(), skillSlug, ownerId, SkillVisibility.PRIVATE));
        SkillVersion published = new SkillVersion(skill.getId(), version, ownerId);
        published.setStatus(SkillVersionStatus.PUBLISHED);
        published.setPublishedAt(Instant.parse("2026-07-28T00:00:00Z"));
        published.setDownloadReady(true);
        published = skillVersionRepository.save(published);
        skill.setLatestVersionId(published.getId());
        skillRepository.save(skill);
        skillRepository.flush();
        skillVersionRepository.flush();
    }

    @Test
    void outsiderCannotResolvePrivateSkill() throws Exception {
        mockMvc.perform(withBearer(
                        get("/api/cli/v1/skills/{namespace}/{slug}/resolve", namespaceSlug, skillSlug),
                        outsiderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void outsiderCannotDownloadLatestPrivateSkill() throws Exception {
        mockMvc.perform(withBearer(
                        get("/api/cli/v1/skills/{namespace}/{slug}/download", namespaceSlug, skillSlug),
                        outsiderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void outsiderCannotDownloadVersionedPrivateSkill() throws Exception {
        mockMvc.perform(withBearer(
                        get("/api/cli/v1/skills/{namespace}/{slug}/versions/{version}/download",
                                namespaceSlug, skillSlug, version),
                        outsiderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void ownerCanResolvePrivateSkill() throws Exception {
        mockMvc.perform(withBearer(
                        get("/api/cli/v1/skills/{namespace}/{slug}/resolve", namespaceSlug, skillSlug),
                        ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value(skillSlug));
    }

    private MockHttpServletRequestBuilder withBearer(
            MockHttpServletRequestBuilder request,
            String rawToken) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + rawToken);
    }
}
```

- [ ] **Step 2: Apply a reversible authorization mutation before the first run**

Temporarily change only the PRIVATE arm in `VisibilityChecker.canAccess`:

```java
case PRIVATE -> true;
```

Do not stage or commit this mutation.

- [ ] **Step 3: Run three independent restricted-read RED commands**

Run the focused Maven command separately for:

```text
CliRestrictedReadAuthorizationIntegrationTest#outsiderCannotResolvePrivateSkill
CliRestrictedReadAuthorizationIntegrationTest#outsiderCannotDownloadLatestPrivateSkill
CliRestrictedReadAuthorizationIntegrationTest#outsiderCannotDownloadVersionedPrivateSkill
```

Expected: each command FAILS because the outsider no longer receives 403. Resolve reaches 200; downloads proceed past authorization and return a non-403 response.

- [ ] **Step 4: Restore PRIVATE authorization and run GREEN commands**

Restore:

```java
case PRIVATE -> isOwner(skill, currentUserId) || isAdminOrAbove(roles.get(skill.getNamespaceId()));
```

Confirm `git diff -- server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/VisibilityChecker.java` is empty. Run all four test methods independently.

Expected: outsider resolve/latest/versioned each PASS with 403; owner resolve PASS with 200.

- [ ] **Step 5: Persist and verify the PRIVATE search-visibility boundary**

Persist a `SkillSearchDocumentEntity` for the same PRIVATE fixture, call the CLI
search endpoint with the valid outsider token through the real
`CliSkillAppService` and `SearchQueryService`, and assert HTTP 200 with the
fixture slug omitted. Run it independently:

```bash
cd server
./mvnw -pl skillhub-app -am \
  -Dtest='CliRestrictedReadAuthorizationIntegrationTest#outsiderSearchOmitsPersistedPrivateSkill' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Before the GREEN run, temporarily include PRIVATE documents in the search
adapter's visibility predicate and confirm the test fails because the fixture
slug appears. Restore the production predicate and confirm the command passes.
The search omission is not a substitute for the real resolve/download 403
assertions above.

- [ ] **Step 6: Commit the restricted-read tests**

```bash
git add server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/cli/CliRestrictedReadAuthorizationIntegrationTest.java
git commit -s -m "test(auth): cover restricted CLI read authorization (#605)"
```

### Task 5: Production-code decision gate

**Files:**
- Inspect only: `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/token/ApiTokenAuthenticationFilter.java`
- Inspect only: `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/token/ApiTokenService.java`

- [ ] **Step 1: Confirm unmodified-source results and production diff**

Run both new classes without any mutation, then run:

```bash
git diff --exit-code origin/main -- \
  server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/token/ApiTokenAuthenticationFilter.java \
  server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/token/ApiTokenService.java
```

Expected: both classes PASS and the production authentication diff is empty. Record the outcome as “current source matrix passes; no production authentication change justified.”

- [ ] **Step 2: Stop for systematic debugging if the expected result is false**

If any unmodified-source assertion fails, stop execution before editing production code. Preserve the failing command and output, invoke `superpowers:systematic-debugging`, trace the request through token persistence, security chains, filters, and endpoint service boundaries, then amend this plan with the confirmed minimal change. Do not continue to documentation with a speculative fix.

### Task 6: Authentication and OpenAPI documentation

**Files:**
- Modify: `docs/03-authentication-design.md`
- Create: `docs/api/authentication.openapi.yaml`

- [ ] **Step 1: Replace the CLI API table with current paths and semantics**

Use this content in section 10.3:

```markdown
### 10.3 CLI API

| 接口 | 凭证规则 | 授权与错误语义 |
|------|---------|---------------|
| `GET /api/cli/v1/auth/whoami` | 有效 Web Session 或有效 Bearer Token | 无有效身份返回 401；坏 Bearer 即使存在 Session 也返回 401 |
| `GET /api/cli/v1/skills/search` | Session 可用；无 Session 时可匿名；提供 Bearer 时必须有效 | 匿名仅返回公开可安装 skill；有效 Bearer 覆盖 Session；坏 Bearer 返回 401，不得降级 |
| `GET /api/cli/v1/skills/{namespace}/{slug}/resolve` | Session 可用；无 Session 时可匿名读取公开资源；提供 Bearer 时必须有效 | 有效 Bearer 覆盖 Session；坏 Bearer 返回 401；有效身份无资源权限返回 403 |
| `GET /api/cli/v1/skills/{namespace}/{slug}/download` | Session 可用；无 Session 时可匿名下载公开资源；提供 Bearer 时必须有效 | 有效 Bearer 覆盖 Session；坏 Bearer 返回 401；有效身份无资源权限返回 403 |
| `GET /api/cli/v1/skills/{namespace}/{slug}/versions/{version}/download` | Session 可用；无 Session 时可匿名下载公开资源；提供 Bearer 时必须有效 | 有效 Bearer 覆盖 Session；坏 Bearer 返回 401；有效身份无资源权限返回 403 |

Spring Security 先加载 Web Session 身份，共享 API token 过滤器随后只处理 Bearer scheme。有效 Bearer 覆盖 Session；坏 Bearer 清除当前身份并立即返回 401，不回退 Session 或匿名。没有 Authorization 或使用 Basic/其他非 Bearer scheme 时保留 Session；如果 Session 也不存在，公共读匿名而 `whoami` 返回 401。身份已验证但 token scope 或资源权限不足时返回 403。`whoami.email` 字段始终存在，没有邮箱时为 `null`。
```

- [ ] **Step 2: Create the complete OpenAPI 3.0 document**

Create `docs/api/authentication.openapi.yaml` with `openapi: 3.0.3`, a `bearerAuth` HTTP bearer security scheme, all five paths, and these exact contract rules:

```yaml
openapi: 3.0.3
info:
  title: SkillHub CLI Authentication API
  version: 1.0.0
  description: >-
    Authentication contract for CLI identity and public skill reads. Valid
    Bearer overrides Web Session. Invalid Bearer returns HTTP 401 without
    Session fallback. An absent Authorization header or unsupported scheme such
    as Basic preserves Session; without Session, public reads are anonymous and
    whoami returns HTTP 401.
servers:
  - url: /
tags:
  - name: CLI Authentication
  - name: CLI Skills
paths:
  /api/cli/v1/auth/whoami:
    get:
      tags: [CLI Authentication]
      summary: Return the current CLI identity
      operationId: cliWhoAmI
      description: Valid Bearer overrides Session; invalid Bearer returns 401 without Session fallback. An absent or non-Bearer Authorization header preserves Session.
      security:
        - bearerAuth: []
        - sessionAuth: []
      responses:
        '200':
          description: Authenticated CLI identity
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/CliWhoAmIEnvelope'
        '401':
          $ref: '#/components/responses/Unauthorized'
  /api/cli/v1/skills/search:
    get:
      tags: [CLI Skills]
      summary: Search CLI-installable skills
      operationId: cliSearchSkills
      description: Valid Bearer overrides Session; invalid Bearer returns 401 without Session fallback. An absent or non-Bearer header preserves Session, otherwise this route uses anonymous public visibility.
      security:
        - {}
        - sessionAuth: []
        - bearerAuth: []
      parameters:
        - name: q
          in: query
          required: false
          schema: {type: string}
          example: pdf
          description: Optional search text.
        - name: limit
          in: query
          required: false
          schema: {type: integer, format: int32, default: 20}
          example: 20
          description: Maximum number of results.
      responses:
        '200':
          description: Search result
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/CliSearchEnvelope'
        '401':
          $ref: '#/components/responses/Unauthorized'
  /api/cli/v1/skills/{namespace}/{slug}/resolve:
    get:
      tags: [CLI Skills]
      summary: Resolve a skill version
      operationId: cliResolveSkill
      description: Valid Bearer overrides Session; invalid Bearer returns 401 without Session fallback. An absent or non-Bearer header preserves Session, otherwise this route uses anonymous public visibility.
      security:
        - {}
        - sessionAuth: []
        - bearerAuth: []
      parameters:
        - $ref: '#/components/parameters/Namespace'
        - $ref: '#/components/parameters/Slug'
        - name: version
          in: query
          required: false
          schema: {type: string}
          example: 1.0.0
          description: Optional exact version; omitted resolves latest.
      responses:
        '200':
          description: Resolved version
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/CliResolveEnvelope'
        '400':
          $ref: '#/components/responses/BadRequest'
        '401':
          $ref: '#/components/responses/Unauthorized'
        '403':
          $ref: '#/components/responses/Forbidden'
  /api/cli/v1/skills/{namespace}/{slug}/download:
    get:
      tags: [CLI Skills]
      summary: Download the latest installable skill version
      operationId: cliDownloadLatestSkill
      description: Valid Bearer overrides Session; invalid Bearer returns 401 without Session fallback. An absent or non-Bearer header preserves Session, otherwise this route uses anonymous public visibility.
      security:
        - {}
        - sessionAuth: []
        - bearerAuth: []
      parameters:
        - $ref: '#/components/parameters/Namespace'
        - $ref: '#/components/parameters/Slug'
      responses:
        '200':
          $ref: '#/components/responses/Download'
        '302':
          $ref: '#/components/responses/DownloadRedirect'
        '400':
          $ref: '#/components/responses/BadRequest'
        '401':
          $ref: '#/components/responses/Unauthorized'
        '403':
          $ref: '#/components/responses/Forbidden'
        '503':
          $ref: '#/components/responses/StorageUnavailable'
  /api/cli/v1/skills/{namespace}/{slug}/versions/{version}/download:
    get:
      tags: [CLI Skills]
      summary: Download an exact installable skill version
      operationId: cliDownloadSkillVersion
      description: Valid Bearer overrides Session; invalid Bearer returns 401 without Session fallback. An absent or non-Bearer header preserves Session, otherwise this route uses anonymous public visibility.
      security:
        - {}
        - sessionAuth: []
        - bearerAuth: []
      parameters:
        - $ref: '#/components/parameters/Namespace'
        - $ref: '#/components/parameters/Slug'
        - $ref: '#/components/parameters/Version'
      responses:
        '200':
          $ref: '#/components/responses/Download'
        '302':
          $ref: '#/components/responses/DownloadRedirect'
        '400':
          $ref: '#/components/responses/BadRequest'
        '401':
          $ref: '#/components/responses/Unauthorized'
        '403':
          $ref: '#/components/responses/Forbidden'
        '503':
          $ref: '#/components/responses/StorageUnavailable'
components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: SkillHub API token
      description: API token issued by SkillHub. Valid Bearer overrides Session; invalid lifecycle states return the same 401 response without Session fallback.
    sessionAuth:
      type: apiKey
      in: cookie
      name: SESSION
      description: Spring Session browser identity, preserved when Authorization is absent or uses a non-Bearer scheme.
  parameters:
    Namespace:
      name: namespace
      in: path
      required: true
      schema: {type: string}
      example: global
      description: Namespace slug.
    Slug:
      name: slug
      in: path
      required: true
      schema: {type: string}
      example: pdf-parser
      description: Skill slug.
    Version:
      name: version
      in: path
      required: true
      schema: {type: string}
      example: 1.0.0
      description: Exact semantic version.
  responses:
    Download:
      description: ZIP package stream
      headers:
        Content-Disposition:
          schema: {type: string}
          description: Attachment filename.
      content:
        application/zip:
          schema: {type: string, format: binary}
    DownloadRedirect:
      description: Redirect to a presigned object-storage URL
      headers:
        Location:
          schema: {type: string, format: uri}
    BadRequest:
      description: Namespace, skill, or version cannot be resolved.
      content:
        application/json:
          schema: {$ref: '#/components/schemas/ErrorEnvelope'}
    Unauthorized:
      description: No valid supported identity is present where required, or the Bearer credential is invalid. Invalid Bearer never falls back to Web Session.
      content:
        application/json:
          schema: {$ref: '#/components/schemas/ErrorEnvelope'}
          example:
            code: 401
            msg: Authentication required
            data: null
            timestamp: '2026-07-28T00:00:00Z'
            requestId: req-123
    Forbidden:
      description: Credential is valid but token scope or resource permission is insufficient.
      content:
        application/json:
          schema: {$ref: '#/components/schemas/ErrorEnvelope'}
          example:
            code: 403
            msg: Forbidden
            data: null
            timestamp: '2026-07-28T00:00:00Z'
            requestId: req-123
    StorageUnavailable:
      description: Object storage is unavailable.
      content:
        application/json:
          schema: {$ref: '#/components/schemas/ErrorEnvelope'}
  schemas:
    Envelope:
      type: object
      required: [code, msg, data, timestamp, requestId]
      properties:
        code: {type: integer, format: int32}
        msg: {type: string}
        data: {type: object, nullable: true}
        timestamp: {type: string, format: date-time}
        requestId: {type: string, example: req-123}
    ErrorEnvelope:
      allOf:
        - $ref: '#/components/schemas/Envelope'
        - type: object
          properties:
            data: {type: object, nullable: true, example: null}
    CliWhoAmIEnvelope:
      allOf:
        - $ref: '#/components/schemas/Envelope'
        - type: object
          properties:
            data:
              $ref: '#/components/schemas/CliWhoAmI'
    CliWhoAmI:
      type: object
      required: [handle, displayName, email]
      properties:
        handle: {type: string, example: user-123}
        displayName: {type: string, example: CLI User}
        email: {type: string, format: email, nullable: true, example: cli@example.com}
    CliSearchEnvelope:
      allOf:
        - $ref: '#/components/schemas/Envelope'
        - type: object
          properties:
            data:
              $ref: '#/components/schemas/CliSearchResult'
    CliSearchResult:
      type: object
      required: [items, total, limit]
      properties:
        items:
          type: array
          items: {$ref: '#/components/schemas/CliSearchItem'}
        total: {type: integer, format: int64, example: 1}
        limit: {type: integer, format: int32, example: 20}
    CliSearchItem:
      type: object
      required: [namespace, slug, latestVersion]
      properties:
        namespace: {type: string, example: global}
        slug: {type: string, example: pdf-parser}
        latestVersion: {type: string, example: 1.2.0}
        summary: {type: string, nullable: true, example: Parse PDF files}
    CliResolveEnvelope:
      allOf:
        - $ref: '#/components/schemas/Envelope'
        - type: object
          properties:
            data:
              $ref: '#/components/schemas/CliResolveResult'
    CliResolveResult:
      type: object
      required: [namespace, slug, version, versionId, fingerprint, downloadUrl]
      properties:
        namespace: {type: string, example: global}
        slug: {type: string, example: pdf-parser}
        version: {type: string, example: 1.2.0}
        versionId: {type: integer, format: int64, example: 42}
        fingerprint: {type: string, example: 'sha256:abc123'}
        downloadUrl: {type: string, example: /api/v1/skills/global/pdf-parser/versions/1.2.0/download}
```

- [ ] **Step 3: Validate documentation formatting and contract paths**

Run:

```bash
ruby -e 'require "yaml"; YAML.load_file("docs/api/authentication.openapi.yaml"); puts "OpenAPI YAML OK"'
rg -n '/api/cli/v1/(auth/whoami|skills)' docs/03-authentication-design.md docs/api/authentication.openapi.yaml
git diff --check
```

Expected: YAML parser prints `OpenAPI YAML OK`, all five current paths are found, and `git diff --check` exits 0.

- [ ] **Step 4: Commit authentication documentation**

```bash
git add docs/03-authentication-design.md docs/api/authentication.openapi.yaml
git commit -s -m "docs(auth): document CLI token failure semantics (#605)"
```

### Task 7: Release artifact and runtime identity evidence

**Files:**
- No repository file changes; evidence belongs in the active issue comment because runtime URLs, replica identities, and operational details may not be suitable for the public repository.

- [ ] **Step 1: Resolve the published v0.2.14 server digest and revision**

Run:

```bash
docker buildx imagetools inspect ghcr.io/iflytek/skillhub-server:v0.2.14
docker buildx imagetools inspect ghcr.io/iflytek/skillhub-server:sha-982258d
```

Expected: record the immutable manifest digest and confirm whether the release tag and SHA tag resolve to the same manifest. If registry access is denied, capture the denial and escalate access to the human owner.

- [ ] **Step 2: Inspect every affected runtime replica when access is provided**

On the runtime host, from the release compose directory, run:

```bash
docker compose -f compose.release.yml config --images
SERVER_CONTAINER_IDS="$(docker compose -f compose.release.yml ps -q server)"
docker inspect --format '{{.Name}} {{.Config.Image}} {{.Image}} {{index .Config.Labels "org.opencontainers.image.revision"}} {{index .Config.Labels "org.opencontainers.image.version"}}' ${SERVER_CONTAINER_IDS}
for container_id in ${SERVER_CONTAINER_IDS}; do
  image_id="$(docker inspect --format '{{.Image}}' "${container_id}")"
  docker image inspect --format '{{json .RepoDigests}}' "${image_id}"
done
```

Expected: record configured version, resolved image reference, image ID, OCI revision/version, and immutable RepoDigest for every replica. A mutable tag alone is not a pass.

- [ ] **Step 3: Replay one token lifecycle against the identified runtime**

Using an authorized dedicated test account, create one token through the normal product flow, verify all five endpoint results while valid, revoke the same token, verify its database `revoked_at` through an authorized operational read, then repeat all five requests with the same raw token. Record HTTP status, response `requestId`, timestamp, and serving replica separately for whoami, search, resolve, latest download, and versioned download. Never paste the raw token into comments or logs.

Expected after revocation: 401 on every endpoint. If behavior differs, preserve the exact digest/replica/request evidence and continue systematic root-cause investigation; do not claim the defect is fixed or closable.

- [ ] **Step 4: Escalate missing runtime authority explicitly**

If no affected runtime URL, host/replica access, or authorization to create/revoke a test token is available, explicitly escalate to the human owner in the active issue. Name the missing authority and request the exact evidence still required: deployed version, immutable server digest or build SHA, all replica identities, and same-token valid-to-revoked replay. State that repository tests do not close the field contradiction and therefore cannot justify closing the defect.

### Task 8: Preserve Web Session fallback and harden the reviewed contracts

**Files:**
- Modify: `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/cli/CliTokenLifecycleSecurityIntegrationTest.java`
- Modify: `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/cli/CliRestrictedReadAuthorizationIntegrationTest.java`
- Modify: `docs/03-authentication-design.md`
- Modify: `docs/api/authentication.openapi.yaml`
- Modify: `docs/superpowers/specs/2026-07-28-revoked-token-validation-design.md`

- [ ] **Step 1: Add the five-endpoint Web Session and mixed-credential matrix**

Add independent arguments for `whoami`, search, resolve, latest download, and
versioned download. For each endpoint exercise Session-only, Session + Basic,
Basic-only, and Session + valid Bearer. Persist distinct Session and token
users, assert Session identity is retained when Bearer is absent or the scheme
is Basic, assert public reads are anonymous for Basic-only, and assert valid
Bearer identity replaces Session identity. Existing revoked, expired, unknown,
empty, and malformed Bearer cases must attach a real mock HTTP Session and
continue to return the fixed five-field 401 envelope before controller service
logic runs.

Run a reversible filter mutation that prevents valid Bearer replacement of an
existing Session principal, then run:

```bash
cd server && ./mvnw -pl skillhub-app -am \
  -Dtest=CliTokenLifecycleSecurityIntegrationTest#sessionAndAuthorizationSchemeMatrix \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected RED: Session + valid Bearer exposes the Session user instead of the
token user. Restore production source immediately and rerun the same command.
Expected GREEN: all 20 endpoint/credential arguments pass without a production
source diff.

- [ ] **Step 2: Lock the nullable whoami email contract**

Persist an active user whose email is `null`, issue its token through
`ApiTokenService`, call `GET /api/cli/v1/auth/whoami`, and assert the `email`
key is present with a JSON null value inside the standard five-field envelope.

```bash
cd server && ./mvnw -pl skillhub-app -am \
  -Dtest=CliTokenLifecycleSecurityIntegrationTest#whoamiReturnsNullEmailForPersistedUserWithoutEmail \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS against existing production behavior; this is a response-shape
characterization test. Update `CliWhoAmI.email` in OpenAPI to remain required
while becoming `nullable: true`.

- [ ] **Step 3: Make PRIVATE search omission a positive and negative proof**

Use a unique numeric `skillSlug` as `q`, persist an installable PUBLIC skill
whose search document contains the same keyword, and keep the existing
installable PRIVATE skill. Assert the PUBLIC slug is returned and the PRIVATE
slug is omitted for the outsider token.

```bash
cd server && ./mvnw -pl skillhub-app -am \
  -Dtest=CliRestrictedReadAuthorizationIntegrationTest#outsiderSearchReturnsMatchingPublicSkillAndOmitsPrivateSkill \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected RED before the PUBLIC fixture is persisted: the expected PUBLIC slug
is absent. Expected GREEN after the fixture is added: the same non-empty result
contains PUBLIC and omits PRIVATE.

- [ ] **Step 4: Assert the fixed five-field 403 envelope on every restricted read**

Replace status/code-only assertions for restricted resolve, latest download,
and versioned download with a shared assertion for exactly `code`, `msg`,
`data`, `timestamp`, and `requestId`; require `code=403`, `data=null`, and
string timestamps/request IDs. Keep the three routes as separate test methods.

```bash
cd server && ./mvnw -pl skillhub-app -am \
  -Dtest=CliRestrictedReadAuthorizationIntegrationTest#outsiderCannotResolvePrivateSkill,CliRestrictedReadAuthorizationIntegrationTest#outsiderCannotDownloadLatestPrivateSkill,CliRestrictedReadAuthorizationIntegrationTest#outsiderCannotDownloadVersionedPrivateSkill \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all three pass through the real access-denied path.

- [ ] **Step 5: Align authentication design and OpenAPI priority rules**

Document these exact rules: valid Bearer overrides Web Session; any Bearer
attempt that is empty, malformed, unknown, expired, revoked, or tied to an
unavailable user returns 401 without Session fallback; no Authorization header
or a non-Bearer scheme preserves a valid Session; without a Session, public
reads use anonymous visibility and `whoami` returns 401. Add cookie
`sessionAuth` to OpenAPI and list it as an alternative on all five operations.
OpenAPI descriptions must state the precedence because security alternatives
cannot encode it alone.

- [ ] **Step 6: Confirm the review correction did not change production auth**

```bash
git diff --name-only origin/main...HEAD
git diff --exit-code origin/main...HEAD -- server/skillhub-auth/src/main server/skillhub-app/src/main
```

Expected: only tests and documentation changed; the production-code diff
command exits 0.

### Task 9: Quality gates and implementation review handoff

**Files:**
- Verify all changed files; do not create a PR in this stage.

- [ ] **Step 1: Run both focused integration classes**

```bash
cd server && ./mvnw -pl skillhub-app -am \
  -Dtest=CliTokenLifecycleSecurityIntegrationTest,CliRestrictedReadAuthorizationIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS, with latest and versioned download reported as distinct methods.

- [ ] **Step 2: Run the complete backend gate**

```bash
make test-backend-app
```

Expected: `BUILD SUCCESS`, zero failures, zero errors.

- [ ] **Step 3: Run repository web gates required before delivery**

```bash
make typecheck-web
make lint-web
```

Expected: zero TypeScript errors and zero ESLint errors/warnings.

- [ ] **Step 4: Run containerized staging regression**

```bash
make staging
```

Expected: backend/frontend images build, services become healthy, and smoke tests pass. Tear down with `make staging-down` after collecting evidence.

- [ ] **Step 5: Verify scope, formatting, and commit hygiene**

```bash
git diff --check origin/main...HEAD
git diff --name-only origin/main...HEAD
git status --short --branch
git log --format='%h %s%n%b' origin/main..HEAD
```

Expected: only the approved spec/plan, two test classes, authentication design, and OpenAPI document are changed; no production authentication source is changed when the matrix passes; all commits are signed off and reference GitHub issue #605 without any Multica identifier.

- [ ] **Step 6: Route to tester and reviewer quality gates**

Provide the branch, focused commands, complete matrix result, 403 fixture result, docs path, runtime identity/replay evidence or explicit external blocker, and full gate output to the project tester. After tester passes, request structured reviewer/security review. Address any findings on the same branch and rerun affected gates.

- [ ] **Step 7: Update the existing single PR and report completion**

Commit and push to the existing `fix/auth-revoked-token-validation` branch so
PR #609 updates in place. Post the implementation result to the active issue
thread. Include commit SHAs, endpoint-by-state matrix, RED mutation evidence,
GREEN results, quality gates, OpenAPI path, production-code decision, and
runtime identity/replay status. Do not create a second PR, do not change issue
status, and do not merge `main` during this stage.
