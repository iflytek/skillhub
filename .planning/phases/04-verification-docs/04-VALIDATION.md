---
phase: 4
slug: verification-docs
status: draft
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-16
---

# Phase 4 — Validation Strategy

> Per-phase validation contract aligned with plans **04-01**, **04-02**, **04-03**.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Backend** | Gradle (`./gradlew`), Java 21, modules `skillhub-app`, `skillhub-domain` |
| **Frontend E2E** | Playwright via `pnpm` in `web/` |
| **Docs** | Docusaurus in `document/` (`npm run build`) |

---

## Sampling Rate

- **Per task:** Run each plan’s `<automated>` verify block after the task’s edits.
- **After wave 1:** All Gradle targets from 04-01 + Playwright specs from 04-02 that exist.
- **After wave 2:** `cd document && npm run build` plus spot-check `docs/e2e.md` links.

---

## Per-Task Verification Map

| Task | Plan | Wave | Requirement | Automated command (from PLAN) | Status |
|------|------|------|-------------|------------------------------|--------|
| T1–T4 | 04-01 | 1 | QA-01 | `./gradlew :server:skillhub-app:test …` / domain tests | ⬜ |
| T1–T4 | 04-02 | 2 | QA-02 | `pnpm exec playwright test …` | ⬜ |
| T1–T4 | 04-03 | 3 | QA-03 | `rg` + `npm run build` in `document/` | ⬜ |

---

## Wave 0 / prerequisites

- [x] Existing Gradle + Playwright + Docusaurus tooling (no new framework install for this phase).

---

## Manual-Only Verifications

| Behavior | Requirement | Instructions |
|----------|-------------|--------------|
| Full CI parity | QA-02 | Optional: run the same job as `pr-e2e.yml` locally if failures are environment-specific |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or documented manual fallback in SUMMARY
- [ ] `nyquist_compliant: true` set in frontmatter after first green wave
- [ ] No watch-mode flags in verify commands

**Approval:** pending
