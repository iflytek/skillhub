---
phase: 5
slug: ux-add-visible-skills
status: draft
nyquist_compliant: false
wave_0_complete: true
created: 2026-04-16
---

# Phase 5 — Validation Strategy

> Per-phase validation contract for add-flow UX rollout.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Vitest + Playwright |
| **Config file** | `web/vite.config.ts`, `web/playwright.config.ts` |
| **Quick run command** | `cd web && pnpm run test -- src/shared/hooks/use-collection-queries.test.ts` |
| **Full suite command** | `cd web && pnpm run test && pnpm run typecheck` |
| **Estimated runtime** | ~180-420 seconds (depends on E2E environment) |

---

## Sampling Rate

- **After every task commit:** run targeted Vitest command for modified files.
- **After every plan wave:** run `cd web && pnpm run test && pnpm run typecheck`.
- **Before `/gsd-verify-work`:** run E2E collection flow spec.
- **Max feedback latency:** 7 minutes.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 05-01-01 | 01 | 1 | COL-04, VIS-03 | T-05-01-01 | Candidate query returns only visibility-safe skills | unit | `cd web && pnpm run test -- src/shared/hooks/use-collection-queries.test.ts` | ✅ | ⬜ pending |
| 05-01-02 | 01 | 1 | INT-01 | T-05-01-02 | Batch add preserves server validation and classifies failures | unit | `cd web && pnpm run test -- src/shared/hooks/use-collection-queries.test.ts` | ✅ | ⬜ pending |
| 05-02-01 | 02 | 2 | WEB-03 | T-05-02-01 | Modal shows only safe candidate metadata and disabled already-added rows | component | `cd web && pnpm run test -- src/features/collection/add-collection-skills-dialog.test.tsx` | ❌ W0 | ⬜ pending |
| 05-02-02 | 02 | 2 | ROL-04 | T-05-02-03 | Add action visible only for owner/contributor/admin-equivalent | component | `cd web && pnpm run test -- src/features/collection/add-collection-skills-dialog.test.tsx` | ❌ W0 | ⬜ pending |
| 05-03-01 | 03 | 3 | QA-02 | T-05-03-01 | E2E uses real UI add-flow path | e2e | `cd web && pnpm run test:e2e -- e2e/collections-flow.spec.ts` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] Existing Vitest and Playwright infrastructure already present.
- [ ] Add `web/src/features/collection/add-collection-skills-dialog.test.tsx`.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Dense modal usability with 100+ results | WEB-03 | Interaction feel and readability cannot be fully asserted by unit tests | Open collection detail, search broad query, verify pagination + selection remain usable |

---

## Validation Sign-Off

- [ ] All tasks include automated verification commands.
- [ ] No three consecutive tasks without an automated check.
- [ ] New component tests for add dialog are present and green.
- [ ] E2E add-flow runs through UI path and passes.
- [ ] `nyquist_compliant: true` set after first full green run.

**Approval:** pending
