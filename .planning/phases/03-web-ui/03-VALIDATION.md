---
phase: 3
slug: web-ui
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-04-15
---

# Phase 3 — Validation Strategy

> Per-phase validation contract aligned with plans **03-01**, **03-02**, **03-03**.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Vitest ^3.2.4; Playwright for E2E when added |
| **Config file** | `web/vite.config.ts` |
| **Quick run command** | `make test-frontend` |
| **Full web unit** | `cd web && pnpm run test` |
| **Backend** | `cd server && ./mvnw -q -pl skillhub-app,skillhub-domain test` |

---

## Sampling Rate

- **Per task:** Use each plan’s `<automated>` verify block.
- **After wave:** `make test-frontend` for web waves; backend test command after plan **02**.

---

## Per-Task Verification Map

| Task | Plan | Wave | Requirement | Automated command (from PLAN) | Status |
|------|------|------|-------------|------------------------------|--------|
| T1 | 03-01 | 1 | WEB-02 | `pnpm run typecheck` (web) | ⬜ |
| T2 | 03-01 | 1 | WEB-01 | `vitest` hook test | ⬜ |
| T3 | 03-01 | 1 | WEB-01,02 | `vitest` router + hooks + `typecheck` | ⬜ |
| T1 | 03-02 | 2 | WEB-03, ROL-06, VIS-03 | `./mvnw ... test` (server) | ⬜ |
| T1 | 03-03 | 3 | WEB-03 | `vitest` hooks + `typecheck` | ⬜ |
| T2 | 03-03 | 3 | WEB-04,05, VIS | `vitest` router + `typecheck` | ⬜ |

---

## Wave 0 / prerequisites

- [ ] OpenAPI regen `pnpm run generate-api` when backend is up (`03-01` Task 1).

---

## Manual-Only Verifications

| Behavior | Requirement | Instructions |
|----------|-------------|--------------|
| Public VIS-03 | VIS-03 | Anonymous vs signed-in on `/u/.../c/...` with seeded data |

---

## Validation Sign-Off

- [ ] All plan tasks include `<automated>` verify
- [ ] `03-RESEARCH.md` open questions resolved
- [ ] `nyquist_compliant: true` after first green wave sampling

**Approval:** pending
