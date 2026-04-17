---
phase: 06
slug: color-scheme-refresh
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-17
---

# Phase 06 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | vitest + playwright + eslint + tsc |
| **Config file** | `web/vitest.config.ts`, `web/playwright.config.ts`, `web/eslint.config.*` (repo default), `web/tsconfig.json` |
| **Quick run command** | `cd web && pnpm run test -- src/app/layout.test.ts src/pages/landing.test.tsx` |
| **Full suite command** | `cd web && pnpm run lint && pnpm run typecheck && pnpm run test` |
| **Estimated runtime** | ~240 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd web && pnpm run test -- src/app/layout.test.ts src/pages/landing.test.tsx`
- **After every plan wave:** Run `cd web && pnpm run lint && pnpm run typecheck && pnpm run test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 240 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 06-01-01 | 01 | 1 | D-05, D-06 | T-06-01-01 | Hardcoded UI colors are blocked and semantic tokens are required with staged enforcement | static | `cd web && node --test scripts/check-no-hardcoded-colors.test.mjs && pnpm run check:colors -- --mode changed && pnpm run check:colors -- --mode baseline-diff --baseline ../.planning/phases/06-color-scheme-refresh/06-COLOR-POLICY-BASELINE.json` | ⬜ pending | ⬜ pending |
| 06-01-02 | 01 | 1 | D-12 | T-06-01-02 | Theme selection follows system by default and persists manual choice | unit | `cd web && pnpm run test -- src/app/layout.test.ts` | ⬜ pending | ⬜ pending |
| 06-02-01 | 02 | 2 | D-14, D-17, D-19 | T-06-02-01 | Shared UI uses semantic tokens with machine-checked motion/effect caps and hidden-toggle testability | unit/component + static | `cd web && pnpm run test -- src/shared/components/landing-quick-start.test.ts src/pages/landing.test.tsx && node --test scripts/check-motion-effects.test.mjs && pnpm run check:motion && pnpm run check:colors -- --mode baseline-diff --baseline ../.planning/phases/06-color-scheme-refresh/06-COLOR-POLICY-BASELINE.json && pnpm run test:e2e -- e2e/theme-bootstrap.spec.ts` | ⬜ pending | ⬜ pending |
| 06-03-01 | 03 | 3 | D-09, D-10, D-16 | T-06-03-01 | Key screens are parity-checked in light/dark with auditable evidence, accessibility gates, and strict repo-wide policy gates | e2e + static + audit | `cd web && pnpm run test:e2e -- e2e/collections-flow.spec.ts && pnpm run check:colors -- --mode strict --scope web/src && pnpm run check:motion && pnpm run check:parity-matrix -- --matrix ../.planning/phases/06-color-scheme-refresh/06-PARITY-MATRIX.md --artifacts-root . && pnpm run test:e2e -- e2e/theme-accessibility.spec.ts` | ⬜ pending | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `web/src/shared/theme/theme-preference.ts` — deterministic system/manual mode resolution helper.
- [ ] `web/src/shared/theme/theme-preference.test.ts` — unit tests for mode resolution and persistence behavior.
- [ ] `web/scripts/check-no-hardcoded-colors.mjs` — static gate for `web/src/**` hardcoded color literals with wave modes (`changed`, `baseline-diff`, `strict`).
- [ ] `web/scripts/check-no-hardcoded-colors.test.mjs` + fixtures `known-good`/`known-bad` — deterministic policy checker regression coverage.
- [ ] `.planning/phases/06-color-scheme-refresh/06-COLOR-POLICY-BASELINE.json` — approved baseline snapshot for Wave 1/2 diff-gate.
- [ ] `web/scripts/check-no-hardcoded-colors.allowlist.json` — formal allowlist entries with owner/approval/expiry.

---

## Hidden Toggle Validation Contract

- `THEME_TOGGLE_RELEASED = false` for Wave 1/2: user-visible toggle must stay hidden in production path.
- Validation is still mandatory and must be explicit:
  - helper-level tests validate resolve/apply/persist logic (`theme-preference`).
  - layout/unit tests can mock `THEME_TOGGLE_RELEASED = true` to test UI branch (`Light/Dark/System`).
  - e2e parity smoke uses seeded storage (`skillhub-theme`) and/or helper path; it must not depend on visible toggle UI before final release gate.
- No-FOUC checks are deterministic only:
  - bootstrap writes `document.documentElement.dataset.themeBootstrap = 'done'` and `dataset.themeMode = '<light|dark>'` before React mount,
  - e2e validates marker presence at `domcontentloaded`; timeout-only assertions are invalid.
- `THEME_TOGGLE_RELEASED` is UX release visibility flag only; it cannot bypass security or policy checks.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Visual readability and contrast consistency on landing + dashboard in both themes | D-09, D-10 | Automated tests cannot fully assess visual quality and subjective readability | Launch `cd web && pnpm run dev`. For Wave 1/2 set theme via seeded storage (`localStorage['skillhub-theme'] = 'light|dark|system'`) and reload; in Wave 3 validate same states через released toggle. Verify text/surface separation and status badge legibility on landing, dashboard, collection detail, skill detail. |

---

## Parity Evidence Contract

`06-PARITY-MATRIX.md` must include two sections: `## State Matrix` and `## Evidence Log`.

Every evidence row is mandatory and machine-auditable by fields:
- `screen`
- `state`
- `theme`
- `steps`
- `expected`
- `actual`
- `artifact_path`
- `owner`
- `timestamp`
- `result` (`PASS|FAIL|N/A`)
- `na_reason`

Decision rules:
- `PASS`: `actual` matches `expected` and `artifact_path` exists.
- `N/A`: allowed only when state is truly not applicable to that screen; `na_reason` is mandatory and reproducible from `steps`.
- Missing/empty mandatory field is treated as `FAIL`.
- `THEME_TOGGLE_RELEASED` may switch to `true` only when all rows are `PASS` or justified `N/A`.
- Automated enforcement is mandatory via:
  `cd web && pnpm run check:parity-matrix -- --matrix ../.planning/phases/06-color-scheme-refresh/06-PARITY-MATRIX.md --artifacts-root .`

---

## Accessibility Gate (Wave 3)

- Required automated gate:
  `cd web && pnpm run test:e2e -- e2e/theme-accessibility.spec.ts`
- Gate must fail on any of:
  - critical contrast below WCAG AA thresholds (normal text < 4.5:1, large text < 3.0:1),
  - missing visible keyboard focus indicator on primary interactive controls,
  - keyboard path breaks/traps for `Tab`, `Shift+Tab`, `Enter`, `Space`, `Escape`.

---

## Plan Metadata Consistency Gate

- Lightweight automated meta-check (must pass before execution):
  `node -e "const fs=require('fs');const plans=['06-01-PLAN.md','06-02-PLAN.md','06-03-PLAN.md'].map(f=>'.planning/phases/06-color-scheme-refresh/'+f);let fail=false;for(const p of plans){const t=fs.readFileSync(p,'utf8');const m=t.match(/files_modified:\\s*([\\s\\S]*?)\\nautonomous:/);const fm=new Set((m?.[1]||'').split('\\n').map(l=>l.trim().replace(/^-\\s*/,'')).filter(Boolean));const tf=[...t.matchAll(/<task[\\s\\S]*?<files>([\\s\\S]*?)<\\/files>/g)].flatMap(x=>x[1].split('\\n').map(l=>l.trim()).filter(l=>l&&!l.startsWith('<')&&!l.startsWith('@')));const missing=[...new Set(tf.filter(f=>!fm.has(f)))];if(missing.length){fail=true;console.error('\\n'+p+'\\nMissing in files_modified:\\n'+missing.join('\\n'));}}if(fail)process.exit(1);"`.
- `check:colors --mode changed` base-ref policy:
  - first use explicit `--base-ref`, then `CHECK_COLORS_BASE_REF`,
  - if missing, checker must fallback to `origin/main`,
  - if `origin/main` unavailable, fallback to `git merge-base HEAD HEAD~1`,
  - if still unresolved, fail with actionable message.

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 240s
- [ ] `check:colors` mode policy respected by wave (Wave 1/2 staged, Wave 3 strict)
- [ ] `check:colors` and `check:motion` fixture tests (`known-good`/`known-bad`) are green
- [ ] `check:motion` passes with D-17/D-19 caps
- [ ] `check:parity-matrix` passes with required fields + artifact existence + PASS/N/A rules
- [ ] Accessibility gate passes (contrast/focus/keyboard)
- [ ] Plan metadata consistency gate passes (`files_modified` covers all `<task><files>` paths)
- [ ] `check:colors --mode changed` fallback policy verified (`--base-ref`/env/`origin/main`/merge-base)
- [ ] `THEME_TOGGLE_RELEASED` used as UX release flag only
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
