# Phase 3: Web UI - Context

**Gathered:** 2026-04-15  
**Status:** Ready for planning

<domain>

## Phase Boundary

Deliver **SkillHub web UI** so authenticated users can complete **WEB-01..WEB-05**: discover and open “My collections”, create/edit collections, view ordered member skills with links to existing skill detail pages, copy/share URLs appropriate to **public vs private**, and **manage contributors** as owner—while **public** collection views respect **VIS-*** (metadata vs filtered skill list). No new product capabilities beyond those requirements.

</domain>

<decisions>

## Implementation Decisions

### Entry point & navigation (WEB-01)

- **D-01:** Surface collections from the **main dashboard** with a **new card** in the same grid pattern as Skills / Tokens / Stars (`DashboardPage` card grid).
- **D-02:** Add a **secondary link in the user menu** to the same collections area (exact menu placement left to implementation; must be discoverable without bookmarking the URL).
- **D-03:** Authenticated management URL prefix: **Claude’s discretion** — choose the prefix that best matches existing `router.tsx` conventions; **recommended default** for planning: `/dashboard/collections/...` alongside other `dashboard/*` routes.
- **D-04:** “My collections” **list** layout: **card grid** (title, visibility, role/relationship to collection, last-updated if available from API).

### Public page, canonical URL & errors (WEB-04, VIS-02, VIS-03)

- **D-05:** **Public** collection page path shape: **`/u/{ownerKey}/c/{collectionSlug}`** (or backend-aligned equivalent with the same segments), consistent with Phase 1 share-URL groundwork.
- **D-06:** For **anonymous** viewers on **public** collections: show the **ordered skill list filtered** to skills they are allowed to see (**VIS-03**); do **not** hide the entire list solely because the viewer is anonymous.
- **D-07:** **Private** collection share: copying the URL is allowed, but show a **short notice** that viewers must be **signed in with access** (toast or inline helper near the copy action).
- **D-08:** Wrong slug / **private** collection without permission: use a **generic “not found”** presentation in both cases (avoid confirming existence of private collections to strangers).

### Contributor “blind” skills (REQUIREMENTS + WEB-03)

- **D-09:** On collection **detail**, when the acting user is a **contributor** and some member skills are invisible to them: show a **non-leaking count + short helper text** (e.g. that additional items exist only for the owner); do **not** use silent-only omission as the sole pattern.
- **D-10:** **Owner** view: **full list** with **no** extra educational banner unless implementation finds a high-value one-line hint (default: none).
- **D-11:** **Contributor reordering** applies to **skills they can see**; mapping to persisted global order must stay correct server-side—UI does not need to expose hidden “slots” (simplest UX).

### Detail, reorder & empty states (WEB-03)

- **D-12:** Reorder interaction: **up/down controls per row** (no drag-and-drop library requirement for v1 unless planner finds an existing in-app DnD pattern worth reusing).
- **D-13:** **Empty** collection (no skills): **prominent “Add skill”** call-to-action with a short explanation.
- **D-14:** Skill rows: show **title + namespace** (and optional small badges if already standard elsewhere) to disambiguate skills; primary action remains link to **existing skill detail** routes.
- **D-15:** **Contributor** experience: **hide or disable** disallowed controls **inline**; **no** persistent “contributor mode” banner.

### Contributor management (WEB-05)

- **D-16:** **Add contributor** flow: **mirror** the user discovery / selection patterns used on **namespace members** (`namespace-members.tsx` and related hooks/components).
- **D-17:** **Remove contributor**: require a **confirm dialog** before removal.
- **D-18:** Contributor management surface: a **section on the collection detail page** (same route; scroll or panel), not a separate contributors-only route by default.
- **D-19:** **Empty contributors list**: **minimal** UI (button-first is acceptable); no lengthy explanatory copy required.

### Claude's Discretion

- Exact **route tree** and **menu item labels/i18n keys** under the above constraints.
- Whether `/dashboard/collections` uses list/detail child routes mirroring namespaces vs a flatter structure—must stay idiomatic to TanStack Router usage in this repo.
- Fine-grained loading/error components as long as **D-08** holds for permission errors.

</decisions>

<canonical_refs>

## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements & roadmap

- `.planning/REQUIREMENTS.md` — **WEB-01..WEB-05**, **VIS-01..VIS-03**, contributor matrix (**ROL-***), MVP table (contributor vs owner powers).
- `.planning/ROADMAP.md` — Phase 3 goal, success criteria, suggested plan split **03-01** / **03-02**.
- `.planning/PROJECT.md` — milestone constraints (session auth, reuse visibility stack, no forked rules).

### Prior phase context

- `.planning/phases/01-domain-persistence/01-CONTEXT.md` — owner-scoped **slug**, surrogate id, share URL intent **`/u/{ownerKey}/c/{collectionSlug}`**, cascade delete semantics.

### Research

- `docs/2026-04-15-skill-collections-research.md` — product background and pitfalls.
- `.planning/research/SUMMARY.md` — aggregate sketch and visibility notes.

### Web implementation anchors

- `web/src/app/router.tsx` — TanStack Router patterns, `requireAuth`, dashboard route family.
- `web/src/pages/dashboard.tsx` — dashboard **card grid** entry-point pattern for new features.
- `web/src/pages/dashboard/namespace-members.tsx` — **reference UX** for member lookup/add/remove flows (**D-16**).

### Backend contracts (Phase 2)

- Planners should trace **actual** REST paths and DTOs from implemented Spring controllers and OpenAPI (paths are not duplicated here to avoid drift); start from `SkillCollectionController` / `PublicSkillCollectionController` in `server/` and generated API types in `web/`.

</canonical_refs>

<code_context>

## Existing Code Insights

### Reusable Assets

- **Routing & auth:** `web/src/app/router.tsx` — lazy routes, `createRequireAuth`, dashboard route patterns.
- **Dashboard IA:** `web/src/pages/dashboard.tsx` — `Card` grid quick links for dashboard features.
- **Member management UX:** `web/src/pages/dashboard/namespace-members.tsx` (+ tests) as the **template** for contributor add/remove flows.

### Established Patterns

- **UI primitives:** `web/src/shared/ui/card` and related dashboard styling (`APP_SHELL_PAGE_CLASS_NAME`).
- **i18n:** `react-i18next` used on dashboard strings — new screens should follow the same approach.

### Integration Points

- New **dashboard** routes must register in `routeTree` alongside existing `dashboard/*` children.
- **Public** collection route will be a **non-dashboard** path (likely top-level) with different `beforeLoad`/`auth` rules than authenticated management routes—planner must align with **RouteSecurityPolicyRegistry** / portal stance already implemented server-side.

</code_context>

<specifics>

## Specific Ideas

- User explicitly chose **dashboard card + user menu** for discovery, **`/u/.../c/...`** for public pages, **filtered** anonymous skill lists, **generic 404** for forbidden/missing, **toast/helper** when copying private links, **count + helper** for contributor-hidden items, **arrow** reordering, **namespace-members-style** contributor picker, **confirm** on contributor removal, **detail-page section** for contributors, and **minimal** empty contributor list.

</specifics>

<deferred>

## Deferred Ideas

None raised during this discussion — scope stayed on **WEB-*** and supporting **VIS-*** presentation.

</deferred>

---

*Phase: 03-web-ui*  
*Context gathered: 2026-04-15*
