---
phase: 3
slug: web-ui
status: draft
shadcn_initialized: false
preset: none
created: 2026-04-15
---

# Phase 3 — UI Design Contract

> Visual and interaction contract for SkillHub collections (WEB-01..WEB-05). Grounded in existing portal patterns (`dashboard.tsx`, `namespace-members.tsx`).

---

## Design System

| Property | Value |
|----------|-------|
| Tool | none (Tailwind utility + local `web/src/shared/ui/*` components) |
| Preset | Tailwind 3.4 + existing dashboard layout tokens |
| Component library | Radix primitives where already used (`@radix-ui/react-dropdown-menu`, `@radix-ui/react-select`) |
| Icon library | `lucide-react` |
| Font | Inherited from app shell (same as dashboard) |

---

## Spacing Scale

Declared values (must be multiples of 4):

| Token | Value | Usage |
|-------|-------|-------|
| xs | 4px | Icon gaps, inline padding |
| sm | 8px | Compact element spacing |
| md | 16px | Default element spacing |
| lg | 24px | Section padding |
| xl | 32px | Layout gaps |
| 2xl | 48px | Major section breaks |
| 3xl | 64px | Page-level spacing |

Exceptions: Reuse `APP_SHELL_PAGE_CLASS_NAME` and dashboard card padding as on `DashboardPage`.

---

## Typography

| Role | Size | Weight | Line Height |
|------|------|--------|---------------|
| Body | inherit (text-sm / text-base per context) | normal | default |
| Label | text-sm | medium | tight |
| Heading | text-lg–2xl | semibold | snug |
| Display | text-2xl | bold | tight (collection title on detail/public) |

---

## Color

| Role | Value | Usage |
|------|-------|-------|
| Dominant (60%) | existing page background | Shell |
| Secondary (30%) | `Card` surfaces | List/detail panels |
| Accent (10%) | primary button / link color from theme | Primary CTAs only |
| Destructive | destructive variant on buttons | Remove contributor, delete collection |

Accent reserved for: primary actions (Create collection, Add skill, Save), not every icon button.

---

## Copywriting Contract

| Element | Copy |
|---------|------|
| Primary CTA | “Create collection” / “Add skill” / “Add contributor” |
| Empty state heading | “No skills yet” (collections detail) |
| Empty state body | Short line: add skills to build this collection; button to open add flow |
| Error state | Problem + next step (“Try again” / “Go back to collections”) |
| Destructive confirmation | “Remove contributor”: confirm they lose access to add/remove/reorder skills in this collection |

---

## Registry Safety

| Registry | Blocks Used | Safety Gate |
|----------|-------------|-------------|
| shadcn official | none | not required |
| Radix (existing) | dropdown, select | follow existing imports only |

---

## Screen contracts (phase-specific)

### WEB-01 — Entry

- Dashboard: new card in same grid as Skills/Tokens/Stars; links to collections list.
- User menu: secondary text link to same list route (discoverable).

### WEB-02 — Create / edit

- Form fields: title, description, visibility (public/private); inline/server validation messages.
- No contributor or reorder on create-only screen unless product flow demands (default: create then land on detail).

### WEB-03 — Detail

- Ordered rows: title + namespace; link to existing skill detail route.
- Reorder: per-row up/down only (v1).
- Empty: prominent “Add skill” CTA.
- Contributor sees hidden-skill helper + non-leaking count when API supports; if API cannot expose count, plan documents fallback (do not use omission-only).

### WEB-04 — Share

- Public canonical path: `/u/{ownerKey}/c/{collectionSlug}`.
- Private: copy allowed with short notice near action (signed-in + access required).
- Missing/forbidden: generic not-found (no “private collection exists” wording).

### WEB-05 — Contributors (owner)

- Section on same detail page; add flow mirrors namespace member discovery; remove requires confirm dialog.

---

## Checker Sign-Off

- [ ] Dimension 1 Copywriting: PASS
- [ ] Dimension 2 Visuals: PASS
- [ ] Dimension 3 Color: PASS
- [ ] Dimension 4 Typography: PASS
- [ ] Dimension 5 Spacing: PASS
- [ ] Dimension 6 Registry Safety: PASS

**Approval:** pending
