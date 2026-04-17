---
phase: 06-color-scheme-refresh
plan: "02"
subsystem: ui
tags: [react, tailwind, theme, dark-mode, playwright, vitest]
requires:
  - phase: 06-color-scheme-refresh-01
    provides: semantic theme tokens, bootstrap theme preference helpers, color policy checks
provides:
  - release-gated shell theme toggle contract with hidden-by-default production path
  - semantic-token migration for shared primitives and auth/dashboard baseline pages
  - motion policy checker and parity smoke e2e coverage for theme bootstrap/persistence
affects: [06-03, ui-parity, auth-screens, shared-primitives]
tech-stack:
  added: []
  patterns:
    - release-flag UX gating via dedicated module export
    - semantic token-only class policy for shell/primitives/auth surfaces
    - e2e bootstrap parity checks with storage-seeded mode transitions
key-files:
  created:
    - web/src/shared/theme/theme-release.ts
    - web/src/shared/theme/theme-release.test.ts
    - web/scripts/check-motion-effects.mjs
    - web/scripts/check-motion-effects.test.mjs
    - web/scripts/__fixtures__/check-motion/known-good.tsx
    - web/scripts/__fixtures__/check-motion/known-bad.tsx
    - web/e2e/theme-bootstrap.spec.ts
  modified:
    - web/src/app/layout.tsx
    - web/src/app/layout.test.ts
    - web/src/shared/theme/theme-preference.ts
    - web/src/shared/ui/button.tsx
    - web/src/shared/ui/button.test.ts
    - web/src/shared/ui/card.tsx
    - web/src/shared/ui/input.tsx
    - web/src/shared/ui/tabs.tsx
    - web/src/pages/dashboard.tsx
    - web/src/pages/login.tsx
    - web/src/pages/register.tsx
    - web/src/pages/reset-password.tsx
    - web/package.json
key-decisions:
  - "THEME_TOGGLE_RELEASED remains false in production path; it gates UX visibility only."
  - "Theme parity is validated via helper/storage contract tests and e2e smoke, not visible toggle interaction."
  - "For this environment, changed-scope color validation used strict scans on touched files plus baseline-diff due git-ref resolution limits in relay workspace."
patterns-established:
  - "Shell controls: release-gated UI + persistent theme-preference helpers"
  - "Auth/dashboard visual states: semantic state tokens (state-danger/state-success/state-warning)"
requirements-completed: [D-04, D-09, D-11, D-12, D-17, D-18, D-19, D-20]
duration: 35 min
completed: 2026-04-17
---

# Phase 6 Plan 02: Shell And Shared Component Migration Summary

**Shipped release-gated shell theme controls, semantic-token migration for core shared/auth/dashboard surfaces, and automated motion/bootstrap parity guards for Wave 2.**

## Performance

- **Duration:** 35 min
- **Started:** 2026-04-17T16:09:57+03:00
- **Completed:** 2026-04-17T16:45:00+03:00
- **Tasks:** 4
- **Files modified:** 20

## Accomplishments
- Added RED-first coverage for hidden/visible theme toggle behavior and semantic shell assertions.
- Implemented `theme-release` gate, compact toggle labels (`Light`, `Dark`, `System`), and removed legacy shell orb/gradient dependency.
- Migrated shared primitives and auth/dashboard pages from hardcoded palette classes to semantic tokens.
- Added e2e smoke (`theme-bootstrap.spec.ts`) for no-FOUC markers, hidden-toggle contract path, persistence, and system-mode parity.

## Task Commits

1. **Task 1: Add failing shell tests for release-gated theme toggle and semantic shell classes** - `a553f7ea` (`test`)
2. **Task 2: Integrate release-gated theme toggle into app shell and remove legacy shell effects** - `f02f52e2` (`feat`)
3. **Task 3: Migrate auth/dashboard shared visual states to semantic tokens** - `70ecee63` (`feat`)
4. **Task 4: Add early parity smoke for bootstrap/persistence and no-FOUC guard** - `d2044ae5` (`test`)

## Files Created/Modified
- `web/src/shared/theme/theme-release.ts` - release visibility flag (`THEME_TOGGLE_RELEASED = false`) for shell toggle UX.
- `web/src/app/layout.tsx` - gated theme control wiring, semantic shell classes, legacy orb removal.
- `web/scripts/check-motion-effects.mjs` - guardrails for infinite motion bans and effect caps.
- `web/src/shared/ui/{button,card,input,tabs}.tsx` - semantic token migration for shared primitives.
- `web/src/pages/{dashboard,login,register,reset-password}.tsx` - semantic state/surface migration for Wave 2 baseline screens.
- `web/e2e/theme-bootstrap.spec.ts` - no-FOUC/bootstrap marker/persistence/system-mode smoke coverage.

## Decisions Made
- Kept theme toggle hidden in production via `THEME_TOGGLE_RELEASED = false`, while fully wiring persistence contract in shell.
- Locked toggle labels to exact test contract (`Light`, `Dark`, `System`) to prevent i18n/UX drift before Wave 3 sign-off.
- Used deterministic data markers (`data-theme-bootstrap`, `data-theme-mode`) as parity oracle at `domcontentloaded`.

## Verification

- `rtk node node_modules/vitest/vitest.mjs run src/app/layout.test.ts src/shared/theme/theme-release.test.ts` -> passed (6/6).
- `node scripts/check-no-hardcoded-colors.mjs --mode strict --scope <each touched Task 3 file>` -> passed (0 violations per file).
- `node scripts/check-no-hardcoded-colors.mjs --mode baseline-diff --baseline ../.planning/phases/06-color-scheme-refresh/06-COLOR-POLICY-BASELINE.json` -> passed (0 new).
- `rtk node --test scripts/check-motion-effects.test.mjs` -> passed (2/2).
- `node scripts/check-motion-effects.mjs` -> passed.
- `rtk node node_modules/@playwright/test/cli.js test e2e/theme-bootstrap.spec.ts` -> passed (3/3).
- `node node_modules/typescript/bin/tsc --noEmit` -> passed.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed Task 4 bootstrap scenario mismatch on reload**
- **Found during:** Task 4 verification
- **Issue:** Initial `addInitScript` rewrote `skillhub-theme` on every reload, causing expected mode transitions (`dark -> light`, `system -> dark`) to fail.
- **Fix:** Updated e2e logic to avoid destructive reseeding across reloads and switched mocked system preference control to a storage-backed flag consumed by `matchMedia`.
- **Files modified:** `web/e2e/theme-bootstrap.spec.ts`
- **Verification:** Relay Playwright run passed all 3 tests.
- **Committed in:** `d2044ae5`

**2. [Rule 3 - Blocking] Adapted changed-scope color verification for relay workspace constraints**
- **Found during:** Task 3 verification
- **Issue:** `check:colors --mode changed` could not resolve git base refs in isolated relay/sandbox contexts.
- **Fix:** Replaced changed-scope gate with strict scanning of each touched Task 3 file plus mandatory baseline-diff check to preserve no-new-debt guarantee.
- **Files modified:** None (verification-path adaptation only)
- **Verification:** Strict scans (all touched files) and baseline-diff both passed.
- **Committed in:** N/A (no source edit)

---

**Total deviations:** 2 auto-fixed (1 bug, 1 blocking)
**Impact on plan:** No scope expansion; all mitigations preserved Wave 2 objectives and verification confidence.

## Authentication Gates

None.

## Issues Encountered

- Local sandbox blocked process spawning for some Node-based test runners (`spawn EPERM`), so equivalent verifications were executed via relay where required.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Wave 2 shell/primitives/auth/dashboard migration is complete and verified.
- Ready for Wave 3 parity/sign-off work with existing bootstrap/motion/color policy guards.

---
*Phase: 06-color-scheme-refresh*
*Completed: 2026-04-17*

## Self-Check: PASSED

- FOUND: `.planning/phases/06-color-scheme-refresh/06-02-SUMMARY.md`
- FOUND commit: `a553f7ea`
- FOUND commit: `f02f52e2`
- FOUND commit: `70ecee63`
- FOUND commit: `d2044ae5`
