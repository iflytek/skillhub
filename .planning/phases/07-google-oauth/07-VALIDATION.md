---
phase: 07
slug: google-oauth
status: draft
nyquist_compliant: false
wave_0_complete: true
created: 2026-04-17
---

# Phase 07 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Vitest |
| **Config file** | `server/pom.xml`, `web/package.json`, `web/vitest.config.ts` |
| **Quick run command** | `cd server && .\\mvnw.cmd -pl skillhub-auth,skillhub-app -Dtest=GoogleClaimsExtractorTest,OAuthLoginFlowServiceTest,AuthControllerTest test` |
| **Full suite command** | `cd server && .\\mvnw.cmd test` and `cd web && pnpm run test` |
| **Estimated runtime** | ~240 seconds |

---

## Sampling Rate

- **After every task commit:** Run backend or web quick command relevant to changed files.
- **After every plan wave:** Run both quick commands (backend + web).
- **Before `/gsd-verify-work`:** Full backend and web suites must be green.
- **Max feedback latency:** 300 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 07-01-01 | 01 | 1 | OAUTH-01, OAUTH-02 | T-07-01-01 | Google claims normalized with deterministic subject/email mapping | unit | `cd server && .\\mvnw.cmd -pl skillhub-auth -Dtest=GoogleClaimsExtractorTest test` | ✅ | ⬜ pending |
| 07-01-02 | 01 | 1 | SEC-01, SEC-02 | T-07-01-02 | Existing sanitize/policy/bind pipeline reused without bypasses | integration | `cd server && .\\mvnw.cmd -pl skillhub-auth -Dtest=OAuthLoginFlowServiceTest test` | ✅ | ⬜ pending |
| 07-02-01 | 02 | 1 | OAUTH-05 | T-07-02-01 | Login UI renders Google entry from backend methods and redirects via actionUrl | unit | `cd web && pnpm run test -- src/features/auth/login-button.test.ts src/pages/login.test.tsx` | ✅ | ⬜ pending |
| 07-03-01 | 03 | 2 | OAUTH-04, QA-01 | T-07-03-01 | Providers/method catalog exposes Google when config present | integration | `cd server && .\\mvnw.cmd -pl skillhub-app -Dtest=AuthControllerTest test` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- Existing infrastructure covers all phase requirements.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Google callback smoke in non-prod env | OAUTH-06 | Requires real OAuth credentials and browser redirect cycle | Configure test Google OAuth app, execute `/oauth2/authorization/google`, verify success redirect and session bootstrap |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 300s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
