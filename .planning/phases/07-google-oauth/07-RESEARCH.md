# Phase 7: Google OAuth - Research

**Researched:** 2026-04-17  
**Domain:** Server OAuth pipeline + web login UX  
**Confidence:** HIGH (core auth flow owners, provider catalog, and login UI entry points audited)

<user_constraints>

## Locked Decisions From Phase Context

- Add Google as a normal OAuth provider, no parallel auth pipeline.
- Preserve existing security invariants for returnTo sanitization and policy checks.
- Keep frontend provider rendering data-driven from backend catalog.
- Deliver test-backed integration with no regressions for existing providers.

</user_constraints>

## Verified Current State

### 1) OAuth backend is already provider-extensible

- `OAuthLoginFlowService` resolves provider via `registrationId` and looks up extractor from a map.
- `OAuthClaimsExtractor` interface is stable and currently implemented by `GitHubClaimsExtractor`.
- Missing extractor for `google` causes `unsupported_provider` failure path.

### 2) Provider catalog is dynamic and config-driven

- `AuthMethodCatalog` builds OAuth providers from `OAuth2ClientProperties.registration`.
- No backend code change required to "list provider" once Google registration exists, but tests must lock behavior.

### 3) Frontend login is already method-driven

- `login.tsx` delegates OAuth entries to `LoginButton`.
- `LoginButton` renders all `OAUTH_REDIRECT` methods and redirects to `actionUrl`.
- Existing visuals are GitHub-oriented (single SVG icon), so provider-specific icon/copy polish is reasonable in this phase.

### 4) Existing tests already cover provider lists and returnTo for github/gitee

- `AuthControllerTest` asserts providers/methods lists and `returnTo` propagation.
- Extending those tests for Google gives high-signal integration confidence.

## Gap Summary

1. No Google-specific `OAuthClaimsExtractor` exists in `skillhub-auth`.
2. No backend tests assert Google provider appears in providers/method catalog.
3. Login button UI lacks provider-specific affordance for Google entry.
4. Ops docs mention GitHub keys; Google config guidance is incomplete.

## Recommended Plan Structure

### Plan 01 (Backend integration)

- Add `GoogleClaimsExtractor`.
- Add extractor-focused tests and flow-level tests.
- Extend auth catalog integration tests with Google registration properties.

### Plan 02 (Web login integration)

- Add provider-aware rendering for Google button.
- Extend login button/login page tests for Google action URL and text rendering.
- Keep data contract unchanged (`/api/v1/auth/methods` remains source of truth).

### Plan 03 (Verification/docs)

- Run focused backend+web suites for Google provider.
- Document required Spring OAuth properties and deployment env variables.
- Add rollout checklist with failure-mode validation (`access_denied`, pending, disabled, invalid returnTo).

## Risk Register

| Risk | Impact | Mitigation |
|------|--------|------------|
| Wrong/partial Google claims mapping creates incorrect account binding | HIGH | Dedicated extractor tests for subject/email/email_verified permutations |
| Redirect handling regression introduces open redirect | HIGH | Keep flow in existing sanitize/consume helpers; integration tests for malicious returnTo |
| UI advertises Google even when backend config missing | MEDIUM | Backend remains source of truth; frontend renders only methods returned by API |
| Existing provider behavior regresses (github/gitee) | MEDIUM | Keep legacy tests in same suite and run full auth catalog assertions |

## Validation Architecture

### Test framework and commands

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + Vitest |
| Backend quick command | `cd server && .\\mvnw.cmd -pl skillhub-auth,skillhub-app -Dtest=GoogleClaimsExtractorTest,OAuthLoginFlowServiceTest,AuthControllerTest test` |
| Web quick command | `cd web && pnpm run test -- src/features/auth/login-button.test.ts src/pages/login.test.tsx` |
| Static checks | `cd web && pnpm run typecheck` |

### Evidence map

| Requirement | Evidence target |
|-------------|-----------------|
| OAUTH-01..03 | `GoogleClaimsExtractor` + extractor tests + flow tests |
| OAUTH-04..06 | `AuthControllerTest` provider/method assertions + login UI tests |
| SEC-01..03 | returnTo sanitization tests + no new custom security chain |
| QA-01..02 | Green backend/web targeted suites |

---

*Phase: 07-google-oauth*  
*Research completed: 2026-04-17*
