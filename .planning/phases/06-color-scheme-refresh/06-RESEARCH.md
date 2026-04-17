# Phase 6: Color Scheme Refresh — Research

**Researched:** 2026-04-17  
**Domain:** Web theming system (React + Tailwind + CSS variables)  
**Confidence:** HIGH (core theme files, app shell, landing, and representative feature pages audited)

<user_constraints>

## Locked Decisions From Phase Context

- Visual direction: **Enterprise Blue/Slate**, flat-first, single-accent primary.
- Token contract: **semantic-first** with strict prohibition of hardcoded UI colors in `web/src/**`.
- Dark mode: **full light/dark parity now** with screen-level matrix checks.
- Theme UX: **system + manual toggle** with persistent user preference.
- Rollout: staged inside Phase 6, Wave 1 fixed to theme core, strict completion gate.
- Effects policy: global effects allowed only in toned-down form; legacy `glow-orb-*`, `feature-icon`, `animate-float` should be removed/replaced in-phase.

</user_constraints>

## Verified Current State

### 1) Theme infrastructure exists but still mixed and inconsistent

- `web/src/index.css` already has root/dark token blocks and semantic variables (`--background`, `--foreground`, `--primary`, etc.).
- At the same time, the file still includes many hardcoded values (`#6A6DFF`, `#B85EFF`, `#16a34a`, `#3b82f6`, literal rgba/hex), plus brand gradient utilities tied to old palette.
- `web/tailwind.config.ts` maps standard semantic tokens only; extended state/stroke semantics are not fully normalized.

### 2) Hardcoded color usage is widespread across UI modules

- `rg` audit across `web/src/**` shows widespread direct color classes (`bg-emerald-500`, `text-red-600`, `bg-blue-100`, etc.), inline style literals, and page-local color logic.
- High-concentration hotspots: `landing.tsx`, auth pages, dashboard/status badges, notifications, security-audit badges, namespace and skill status components.
- Current state violates D-05/D-06 (semantic-first + strict no-hardcoded rule).

### 3) Dark mode contract is incomplete

- `.dark` tokens exist in CSS, but there is no explicit global theme preference controller (system/manual toggle + persistence).
- `web/src/main.tsx` mounts app without theme bootstrap logic.
- `web/src/app/layout.tsx` has no theme switch control and no boot-time strategy to avoid flicker/mismatch.
- Multiple pages still rely on light-specific hardcoded colors, preventing parity.

### 4) Legacy decorative effects are still active

- `index.css` still defines and uses old effect utilities: `glow-orb-*`, `feature-icon`, `animate-float`, aggressive gradients and glow shadows.
- `layout.tsx` still renders decorative gradient orb in shell, and landing sections still depend on brand gradient-heavy blocks.
- This conflicts with D-04 and D-20 unless migrated to toned-down semantic effects.

## Gap Summary (What planning must close)

1. Establish normalized semantic token taxonomy (`surface/content/action/state/stroke`) with explicit light/dark mappings.
2. Introduce deterministic theme mode infrastructure (system/manual toggle + persisted preference).
3. Remove hardcoded UI colors from `web/src/**` by replacing with semantic token classes/variables.
4. Replace legacy effects with bounded, token-driven alternatives (or remove where unnecessary).
5. Add automated policy checks to prevent reintroduction of hardcoded colors.
6. Add parity validation matrix for key screens and critical flows in both themes.

## Recommended Rollout Strategy

### Wave 1 (Theme Core Foundation)

- Refactor `index.css` token set to Enterprise Blue/Slate semantics.
- Expand token groups to include explicit `state` and `stroke` aliases for component usage.
- Align `tailwind.config.ts` with semantic tokens (including state and stroke families).
- Implement theme bootstrap + preference persistence (system/manual toggle) in app-level provider/bootstrap files.
- Add lint/check script (or test gate) that fails on new hardcoded color literals under `web/src/**`, with explicit allowlist for non-UI assets.

### Wave 2 (Application Shell + Shared Components)

- Migrate `layout.tsx`, shared primitives (`button`, `card`, `input`, tabs/dropdowns), and global badges/utilities to semantic tokens.
- Remove/replace legacy shell effects and gradient dependencies.
- Ensure auth/dashboard/common flows pass in both light and dark without local hardcoded exceptions.

### Wave 3 (Feature/Page Sweep + Regression Hardening)

- Migrate remaining feature/page hotspots (landing, notifications, security-audit badges, namespace/skill status badges, public pages).
- Add screen matrix checks (light/dark) for critical pages: landing, dashboard, collections detail/list, skill detail, auth pages.
- Lock behavior with targeted tests and update E2E snapshots/assertions where theme-dependent rendering changed.

## Risk Register

| Risk | Impact | Mitigation |
|------|--------|------------|
| Incomplete hardcoded-color migration leaves visual drift | HIGH | Wave-based inventory + automated hardcoded-color gate + final repo-wide scan in verification |
| Theme toggle flicker or hydration mismatch | MEDIUM | Initialize theme before React render and persist preference with safe fallback to system |
| Dark mode regressions in less-used pages | HIGH | Screen matrix + priority sweep list + targeted test assertions for status/badge components |
| Over-aggressive effects removal breaks brand identity | MEDIUM | Keep minimal token-driven decorative set with strict intensity caps instead of blanket deletion where UX value exists |
| Security-status semantics become ambiguous after tokenization | MEDIUM | Introduce explicit semantic state tokens (`state-success`, `state-warning`, `state-danger`, `state-info`) and map all badges consistently |

## Validation Architecture

### Test framework and commands

| Property | Value |
|----------|-------|
| Framework | Vitest + Playwright + ESLint + TypeScript |
| Quick command | `cd web && pnpm run test -- src/app/layout.test.ts src/pages/landing.test.tsx` |
| Full unit/component command | `cd web && pnpm run test` |
| Static checks | `cd web && pnpm run lint && pnpm run typecheck` |
| E2E smoke for key screens | `cd web && pnpm run test:e2e -- e2e/collections-flow.spec.ts` |

### Phase 6 evidence map

| Decision/Scope | Evidence target |
|----------------|-----------------|
| D-05/D-06 semantic-only UI colors | repo-wide hardcoded-color gate + migration commits touching hotspot files |
| D-09/D-10 parity | light/dark matrix checklist and updated tests for key pages |
| D-12 theme UX | system/manual toggle + persisted preference implementation in app shell |
| D-20 legacy effect cleanup | removal/replacement of glow-orb/feature-icon/animate-float dependencies |

---

*Phase: 06-color-scheme-refresh*  
*Research completed: 2026-04-17*
