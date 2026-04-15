# Phase 3: Web UI (Skill collections) — Research

**Researched:** 2026-04-15  
**Domain:** React SPA (TanStack Router + React Query) on Spring `SkillCollectionController` / `PublicSkillCollectionController`  
**Confidence:** MEDIUM (repo + controllers verified; OpenAPI snapshot in `web/` appears stale; contributor reorder vs domain rules needs explicit product/backend alignment)

<user_constraints>

## User Constraints (from CONTEXT.md)

### Locked Decisions

**Implementation Decisions**

#### Entry point & navigation (WEB-01)

- **D-01:** Surface collections from the **main dashboard** with a **new card** in the same grid pattern as Skills / Tokens / Stars (`DashboardPage` card grid).
- **D-02:** Add a **secondary link in the user menu** to the same collections area (exact menu placement left to implementation; must be discoverable without bookmarking the URL).
- **D-03:** Authenticated management URL prefix: **Claude’s discretion** — choose the prefix that best matches existing `router.tsx` conventions; **recommended default** for planning: `/dashboard/collections/...` alongside other `dashboard/*` routes.
- **D-04:** “My collections” **list** layout: **card grid** (title, visibility, role/relationship to collection, last-updated if available from API).

#### Public page, canonical URL & errors (WEB-04, VIS-02, VIS-03)

- **D-05:** **Public** collection page path shape: **`/u/{ownerKey}/c/{collectionSlug}`** (or backend-aligned equivalent with the same segments), consistent with Phase 1 share-URL groundwork.
- **D-06:** For **anonymous** viewers on **public** collections: show the **ordered skill list filtered** to skills they are allowed to see (**VIS-03**); do **not** hide the entire list solely because the viewer is anonymous.
- **D-07:** **Private** collection share: copying the URL is allowed, but show a **short notice** that viewers must be **signed in with access** (toast or inline helper near the copy action).
- **D-08:** Wrong slug / **private** collection without permission: use a **generic “not found”** presentation in both cases (avoid confirming existence of private collections to strangers).

#### Contributor “blind” skills (REQUIREMENTS + WEB-03)

- **D-09:** On collection **detail**, when the acting user is a **contributor** and some member skills are invisible to them: show a **non-leaking count + short helper text** (e.g. that additional items exist only for the owner); do **not** use silent-only omission as the sole pattern.
- **D-10:** **Owner** view: **full list** with **no** extra educational banner unless implementation finds a high-value one-line hint (default: none).
- **D-11:** **Contributor reordering** applies to **skills they can see**; mapping to persisted global order must stay correct server-side—UI does not need to expose hidden “slots” (simplest UX).

#### Detail, reorder & empty states (WEB-03)

- **D-12:** Reorder interaction: **up/down controls per row** (no drag-and-drop library requirement for v1 unless planner finds an existing in-app DnD pattern worth reusing).
- **D-13:** **Empty** collection (no skills): **prominent “Add skill”** call-to-action with a short explanation.
- **D-14:** Skill rows: show **title + namespace** (and optional small badges if already standard elsewhere) to disambiguate skills; primary action remains link to **existing skill detail** routes.
- **D-15:** **Contributor** experience: **hide or disable** disallowed controls **inline**; **no** persistent “contributor mode” banner.

#### Contributor management (WEB-05)

- **D-16:** **Add contributor** flow: **mirror** the user discovery / selection patterns used on **namespace members** (`namespace-members.tsx` and related hooks/components).
- **D-17:** **Remove contributor**: require a **confirm dialog** before removal.
- **D-18:** Contributor management surface: a **section on the collection detail page** (same route; scroll or panel), not a separate contributors-only route by default.
- **D-19:** **Empty contributors list**: **minimal** UI (button-first is acceptable); no lengthy explanatory copy required.

### Claude's Discretion

- Exact **route tree** and **menu item labels/i18n keys** under the above constraints.
- Whether `/dashboard/collections` uses list/detail child routes mirroring namespaces vs a flatter structure—must stay idiomatic to TanStack Router usage in this repo.
- Fine-grained loading/error components as long as **D-08** holds for permission errors.

### Deferred Ideas (OUT OF SCOPE)

None raised during this discussion — scope stayed on **WEB-*** and supporting **VIS-*** presentation.

</user_constraints>

<phase_requirements>

## Phase Requirements

| ID | Description | Research support |
|----|-------------|------------------|
| WEB-01 | “My collections” from dashboard / nav | `DashboardPage` card grid; `UserMenu` links; new `dashboard/collections/*` routes in `router.tsx` [VERIFIED: `web/src/pages/dashboard.tsx`, `web/src/shared/components/user-menu.tsx`, `web/src/app/router.tsx`] |
| WEB-02 | Create/edit collections + validation | `POST /api/web/collections`, `PATCH /api/web/collections/{id}`, `PATCH /api/web/collections/{id}/visibility` + DTO validation messages [VERIFIED: `SkillCollectionController.java`] |
| WEB-03 | Detail: ordered skills → skill detail links | Members are `skillId` + `sortOrder` only; extra fetches needed for title/namespace/slug [VERIFIED: `SkillCollectionMemberResponse.java`]; optional `GET /api/web/skills/id/{skillId}` in OpenAPI schema [VERIFIED: `web/src/api/generated/schema.d.ts` grep] |
| WEB-04 | Share URLs public vs private | Public: `GET /api/web/public/collections/{ownerId}/{slug}` [VERIFIED: `PublicSkillCollectionController.java`]; canonical web path **D-05**; private copy UX **D-07** |
| WEB-05 | Owner contributor list/add/remove | `GET/POST/DELETE` under `/api/web/collections/{id}/contributors` [VERIFIED: `SkillCollectionController.java`]; UX mirror `AddNamespaceMemberDialog` [VERIFIED: `web/src/features/namespace/add-namespace-member-dialog.tsx`] |
| VIS-02 | Public metadata readable per policy | `RouteSecurityPolicyRegistry` permits anonymous `GET` on `/api/web/public/collections/*/*` [VERIFIED: `RouteSecurityPolicyRegistry.java`] |
| VIS-03 | Member list filtered per viewer | `listVisibleMembers` filters with `SkillReadableForActorPort` [VERIFIED: `SkillCollectionPortalQueryAppService.java`] |
| ROL-02 / ROL-03 | Contributor grant/revoke UI | Same contributor endpoints; confirm dialog pattern (`ConfirmDialog` in `namespace-members.tsx`) [VERIFIED: `namespace-members.tsx`] |

</phase_requirements>

## Summary

Phase 3 wires the **existing Phase 2 REST API** into the React app using the same **TanStack Router** (`beforeLoad: requireAuth` on dashboard family), **React Query** hooks, **`web/src/api/client.ts`** (`WEB_API_PREFIX = '/api/web'`, CSRF + `Accept-Language`), and **react-i18next** copy as on the dashboard and namespace members flows.

**Primary recommendation:** Treat **`SkillCollectionController` + `PublicSkillCollectionController`** as the single contract, add a **`collectionApi` + `useCollectionQueries`** layer mirroring `namespaceApi` / `use-namespace-queries.ts`, register **`/dashboard/collections/...`** plus a **public** route **`/u/$ownerKey/c/$slug`** with **no `requireAuth`**, and plan explicit tasks for **three integration gaps** discovered in code: (1) **logged-in viewers** on the public page vs `PublicSkillCollectionController` always passing `null` viewer [VERIFIED: `PublicSkillCollectionController.java` + `getPublicByOwnerAndSlug`]; (2) **D-09 non-leaking hidden count** vs responses that only include **visible** members with no totals [VERIFIED: `SkillCollectionResponse` / `listVisibleMembers`]; (3) **D-11 contributor reorder** vs server **`reorder.setMismatch`** requiring the **full** skill-id set [VERIFIED: `SkillCollectionMembershipService.java`].

## Project Constraints (from .cursor/rules/)

No `.cursor/rules/` directory was found in this workspace snapshot. **Additional directive source:** root `CLAUDE.md` mandates **GitNexus impact analysis before edits** to symbols and **pre-commit `gitnexus_detect_changes`** for code changes [VERIFIED: `CLAUDE.md`]. Planners should treat that as **process constraint** for implementation tasks that touch existing modules.

## Standard Stack

### Core

| Library | Version (repo) | Purpose | Why Standard |
|---------|----------------|---------|--------------|
| React | `^19.0.0` | UI | Already adopted project-wide [VERIFIED: `web/package.json`] |
| TanStack Router | `^1.95.0` | Routing, lazy routes, `beforeLoad` auth | Central registry `web/src/app/router.tsx` [VERIFIED: `web/package.json` + `router.tsx`] |
| TanStack Query | `^5.64.0` | Server state, mutations | Hooks like `use-namespace-queries.ts` [VERIFIED: `web/package.json`] |
| openapi-fetch | `^0.13.8` + `openapi-typescript` | Typed HTTP + generated `paths` | `createClient<paths>` in `web/src/api/client.ts` [VERIFIED: `web/package.json` + `client.ts`] |
| react-i18next | `^16.5.8` | Locale strings | Dashboard + user menu [VERIFIED: `web/package.json` + `dashboard.tsx`] |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Vitest | `^3.2.4` | Unit/component tests | Co-located `*.test.ts(x)` under `web/src` [VERIFIED: `web/package.json` + glob] |
| Playwright | `^1.58.2` | E2E | `pnpm run test:e2e` / smoke config [VERIFIED: `web/package.json`] |
| Zod | — (not listed) | Form validation | Prefer server-driven validation messages from `ApiError` like existing flows [ASSUMED] |

**Version verification:** Package versions taken from `web/package.json` on 2026-04-15 [VERIFIED: `web/package.json`]. Regenerate client types after API changes: `pnpm run generate-api` (requires running backend) [VERIFIED: `web/package.json` script].

## Architecture Patterns

### Recommended project structure (incremental)

```
web/src/
├── app/router.tsx              # register dashboard + public collection routes
├── pages/dashboard/collections-*   # list / new / edit / detail (planner-chosen split)
├── features/collection/        # optional: dialogs, reorder row, share copy
├── shared/hooks/use-collection-queries.ts  # mirror use-namespace-queries
└── api/client.ts               # export collectionApi { ... }
```

### Pattern 1: Dashboard route + `requireAuth`

**What:** Each authenticated dashboard page is a sibling route under `path: 'dashboard/...'` with `beforeLoad: requireAuth` [VERIFIED: `web/src/app/router.tsx`].

**When to use:** All **WEB-01..WEB-03, WEB-05** management screens.

### Pattern 2: Public top-level route (no auth)

**What:** Public collection page should **not** use `beforeLoad: requireAuth`, so anonymous users can load the shell and filtered members per **VIS-03** [VERIFIED: policy `permitAll` on `GET /api/web/public/collections/*/*` in `RouteSecurityPolicyRegistry.java`].

**When to use:** **WEB-04** / **D-05** canonical URL.

### Pattern 3: React Query feature hooks

**What:** Thin `queryFn` + `useQuery` / `useMutation` with stable `queryKey`s and `invalidateQueries` on success — see `use-namespace-queries.ts` [VERIFIED: `web/src/shared/hooks/use-namespace-queries.ts`].

**When to use:** Collections list, detail, contributors, mutations.

### Pattern 4: Member / contributor dialogs

**What:** `AddNamespaceMemberDialog` combines **debounced search**, **minimum length**, **manual userId**, and **toast** feedback [VERIFIED: `web/src/features/namespace/add-namespace-member-dialog.tsx`].

**When to use:** **D-16** contributor add (search is namespace-scoped today; manual `userId` path still matches product “registered user” requirement).

### Anti-patterns to avoid

- **Leaking private collections:** Differentiate UI for 403 vs 404 only if the API distinguishes them; domain uses **`error.skillCollection.notFound`** for forbidden private reads [VERIFIED: `SkillCollectionPortalQueryAppService.getForActor`]. Present **generic Not Found** for strangers per **D-08**.
- **Trusting OpenAPI file without refresh:** `schema.d.ts` had **no** `collections` string match while `schema.d.ts` still documents `/api/web/skills/id/{skillId}` [VERIFIED: grep 2026-04-15]; run **`make generate-api`** or `pnpm run generate-api` after backend is up [VERIFIED: `Makefile` + `web/package.json`].
- **Encoding skill slugs in links:** Follow skill detail route pattern `encodeURIComponent(skill.slug)` [VERIFIED: `web/src/pages/dashboard.tsx`].

## Don't Hand-Roll

| Problem | Don't build | Use instead | Why |
|---------|-------------|-------------|-----|
| HTTP + CSRF + envelope parsing | Raw `fetch` scattered in pages | `fetchJson` / `openapi-fetch` client in `web/src/api/client.ts` | Cookies, `X-XSRF-TOKEN`, i18n headers centralized [VERIFIED: `client.ts`] |
| Auth redirect logic | Per-page `useEffect` redirects | `createRequireAuth` + `beforeLoad` | Already used for dashboard [VERIFIED: `router.tsx`] |
| Contributor picker UX | Brand-new interaction model | `AddNamespaceMemberDialog` patterns (search + userId + toast) | **D-16** explicit [VERIFIED: CONTEXT + dialog source] |
| Public policy bypass | Ad-hoc “public” fetches without checking registry | Align with `RouteSecurityPolicyRegistry` | Session vs anonymous consistency [VERIFIED: `RouteSecurityPolicyRegistry.java`] |

**Key insight:** Backend already centralizes **visibility-safe membership**; the web’s job is to **not undermine** it with extra client-side caches or “preview” calls that expose hidden skills.

## Common Pitfalls

### Pitfall 1: Public controller always uses anonymous viewer for filtering

**What goes wrong:** A **signed-in** user opens `/u/.../c/...`, the page calls **`GET /api/web/public/collections/{ownerId}/{slug}`**, and members are filtered with **`viewerUserIdOrNull = null`**, same as anonymous [VERIFIED: `PublicSkillCollectionController.getPublicCollection` → `getPublicByOwnerAndSlug(..., null)`].

**Why it happens:** Public endpoint does not accept / forward the session principal to `listVisibleMembers`.

**How to avoid:** After first response (for metadata + `id`), if a session exists, call **`GET /api/web/collections/{id}`** so `listVisibleMembers` uses the real `actingUserId` [VERIFIED: `SkillCollectionController.getForActor` + `getForActor` uses non-null viewer]. Confirm **strangers** still receive **only** public-filtered members when using public URL intentionally.

**Warning signs:** Logged-in user sees **fewer** skills than they should on another user’s public collection.

### Pitfall 2: Contributor hidden-item count (D-09) vs API shape

**What goes wrong:** `SkillCollectionResponse.members` is already **visibility-filtered**; there is **no** `totalMembers` / `hiddenCount` field [VERIFIED: `SkillCollectionResponse.java` + `listVisibleMembers`].

**Why it happens:** Product UX (**D-09**) needs aggregate info the DTO does not expose.

**How to avoid:** Plan either a **small backend extension** (non-leaking counts) or a **scoped exception** to UX (document deviation) — do not infer counts from gaps (risks wrong counts if skills deleted).

### Pitfall 3: Contributor reorder vs full membership invariant

**What goes wrong:** `reorderSkills` requires `orderedSkillIds` set to **exactly equal** all member skill IDs in the collection [VERIFIED: `SkillCollectionMembershipService.reorderSkills` lines 85–97].

**Why it happens:** Contributors may not **see** all skill IDs (**D-09**), so a client sending only visible rows **fails** `setMismatch`.

**How to avoid:** Until domain changes, **disable reorder for contributors** when `members.length` implies incomplete view is impossible to detect — practically: **only allow contributor reorder when contributor is also able to see all members** (heuristic: owner-only hidden skills) **or** escalate **backend story** to accept contributor-safe partial reorders. This conflicts with **D-11** as written — **needs explicit resolution** before implementation.

### Pitfall 4: Skill rows need namespace/title (D-14) but DTO only has `skillId`

**What goes wrong:** `SkillCollectionMemberResponse` exposes **`membershipId`, `skillId`, `sortOrder` only** [VERIFIED: `SkillCollectionMemberResponse.java`].

**Why it happens:** Collection aggregate does not embed skill catalog fields.

**How to avoid:** Batch-resolve via **`GET /api/web/skills/id/{skillId}`** (path present in generated OpenAPI file) or reuse an existing summary endpoint if wrapped in `client.ts` during implementation [VERIFIED: `schema.d.ts` path presence; absence of wrapper **verified** by grep of `client.ts`].

### Pitfall 5: Private link copy (D-07) vs generic 404 (D-08)

**What goes wrong:** Over-sharing error text from API could confirm resource existence.

**Why it happens:** UX wants helper on copy, but strangers must see **Not Found**.

**How to avoid:** Keep **copy helper** only in **owner/contributor** authenticated contexts; public/error states stay generic.

## Code Examples

### Authenticated list (contract sketch)

```typescript
// Pattern: same as other portal GETs — use WEB_API_PREFIX and shared fetchJson
// GET ${WEB_API_PREFIX}/me/collections?page=&size=
```

Source: [VERIFIED: `SkillCollectionController.java` `@GetMapping("/me/collections")` + `web/src/api/client.ts` `WEB_API_PREFIX`]

### Public read

```http
GET /api/web/public/collections/{ownerId}/{slug}
```

Source: [VERIFIED: `PublicSkillCollectionController.java`]

### Skill detail navigation (existing)

```tsx
<Link
  to="/space/$namespace/$slug"
  params={{ namespace: skill.namespace, slug: encodeURIComponent(skill.slug) }}
/>
```

Source: [VERIFIED: `web/src/pages/dashboard.tsx`]

## State of the Art

| Old approach | Current approach | When changed | Impact |
|--------------|------------------|--------------|--------|
| Ad-hoc session fetches in components | `createRequireAuth` at route edge | Pre SkillHub collections work | New routes must follow same pattern [VERIFIED: `router.tsx`] |
| Monolithic dashboard | Card grid + deep links | Existing | Extend with one more card [VERIFIED: `dashboard.tsx`] |

**Deprecated/outdated:** None identified for this phase beyond **regenerating OpenAPI** when DTOs landed [VERIFIED: stale `schema.d.ts` signal].

## Assumptions Log

| # | Claim | Section | Risk if wrong |
|---|-------|---------|---------------|
| A1 | Zustand/toast patterns for mutations match namespace flows without new global state | Architecture | Minor refactor if wrong |
| A2 | `ownerKey` in **D-05** maps 1:1 to `SkillCollectionResponse.ownerId` (auth `userId` string) | Public URL | Wrong public links |
| A3 | No separate global “user search” API exists beyond namespace member-candidates | WEB-05 | Contributor onboarding UX weaker |

**If this table is empty:** N/A — assumptions listed for planner triage.

## Open questions (resolved)

1. **D-11 vs server reorder invariant — RESOLVED**  
   - **Decision:** Extend **`SkillCollectionMembershipService.reorderSkills`** (or equivalent) so **contributors** may submit **partial** `orderedSkillIds` (visible subset only); service loads **full** membership and **merges** order without exposing hidden skill ids to the client.  
   - **Rationale:** Matches CONTEXT **D-11** (reorder visible only) without client-side knowledge of hidden ids.  
   - **Fallback:** If merge cannot ship in one iteration, **disable** contributor reorder when `additionalMembersHiddenFromActorCount > 0` until backend ships (document in SUMMARY).

2. **Hidden member count (D-09) — RESOLVED**  
   - **Decision:** Add **one** aggregate numeric field on **`SkillCollectionResponse`** (name TBD in plan, e.g. `additionalMembersHiddenFromActorCount`) populated **only** for **contributor** actors on **authenticated** `GET /api/web/collections/{id}`; **null or 0** for owner, admin, strangers, and **all** public-endpoint responses.  
   - **Rationale:** Satisfies non-leaking count + helper text without listing hidden skills.

3. **Logged-in public page (VIS-03) — RESOLVED**  
   - **Decision:** Sequential **`GET /api/web/public/collections/{ownerKey}/{slug}`** then, when session exists and response includes collection **`id`**, **`GET /api/web/collections/{id}`** and **replace** rendered payload for members/title/description.  
   - **Rationale:** Acceptable for v1 SPA; public SEO is out of scope for this milestone; correctness over extra round trip.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| Spring Boot API on `localhost:8080` | `pnpm run generate-api` | [not probed in sandbox] | — | Manually type `collectionApi` from controllers |
| `pnpm` + Node | Web build/tests | [not probed in sandbox] | See CI / dev machine | Use `Makefile` targets from Git Bash/WSL on Windows |

**Missing dependencies with no fallback:**

- None for **pure UI coding**; **OpenAPI regen** needs a running API [VERIFIED: `generate-api` script].

**Missing dependencies with fallback:**

- Stale `schema.d.ts` → hand-write types temporarily then regen [ASSUMED workflow].

**Step 2.6 note:** Phase is not a rename/migration; **Runtime State Inventory** intentionally omitted.

## Validation Architecture

> `workflow.nyquist_validation` is **true** in `.planning/config.json` [VERIFIED: `.planning/config.json`].

### Test framework

| Property | Value |
|----------|-------|
| Framework | Vitest `^3.2.4` (via Vite `test` config) [VERIFIED: `web/package.json`, `web/vite.config.ts`] |
| Config file | `web/vite.config.ts` (`test.exclude` includes `e2e`) [VERIFIED: `vite.config.ts`] |
| Quick run (repo Makefile) | `make test-frontend` → `cd web && pnpm run test` [VERIFIED: `Makefile`] |
| Full web suite (unit) | Same as quick unless split later: `pnpm run test` [VERIFIED: `Makefile` + `package.json`] |
| E2E (full Playwright) | `make test-e2e-frontend` → `pnpm run test:e2e` [VERIFIED: `Makefile` + `package.json`] |
| E2E smoke | `make test-e2e-smoke-frontend` → `pnpm run test:e2e:smoke` [VERIFIED: `Makefile` + `package.json`] |
| Typecheck / lint | `make typecheck-web` / `make lint-web` [VERIFIED: `Makefile`] |

### Phase requirements → test map

| Req ID | Behavior | Test type | Automated command | File exists? |
|--------|----------|-----------|-------------------|--------------|
| WEB-01 | Dashboard card + menu link | unit / RTL | `pnpm exec vitest run src/pages/dashboard.test.tsx` (extend) or new test beside dashboard | ❌ Wave 0 — add/extend |
| WEB-02 | Create/edit validation | unit + integration | Vitest component tests + optional MSW | ❌ Wave 0 |
| WEB-03 | Ordered links + reorder | unit | Vitest for pure ordering helpers + mutation calls mocked | ❌ Wave 0 |
| WEB-04 | Public vs private share UX | unit + e2e | Playwright under `web/e2e` (pattern TBD) | ❌ Wave 0 |
| WEB-05 | Contributor CRUD | unit | Mirror `namespace-members.test.ts` patterns | ❌ Wave 0 |
| VIS-03 | Filtered list for anonymous | integration/e2e | Backend already IT-heavy; web E2E with seeded data (Phase 4 aligns with **QA-02**) | Partial — server `SkillCollectionSecurityIT` exists [VERIFIED: grep hit] |

### Sampling rate

- **Per task commit:** `cd web; pnpm run test -- --run src/path/to/changed.test.ts` (Vitest file-scoped) [ASSUMED: standard Vitest CLI]
- **Per wave merge:** `make test-frontend`
- **Phase gate:** `make test-frontend && make typecheck-web && make lint-web`; add **`make test-e2e-smoke-frontend`** when first E2E scenario lands [VERIFIED: scripts exist; ordering **ASSUMED**].

### Wave 0 gaps

- [ ] `web/src/shared/hooks/use-collection-queries.ts` (+ tests) — covers data loading for WEB-01/03/05
- [ ] `collectionApi` section in `web/src/api/client.ts` — typed calls for all collection endpoints
- [ ] `web/src/app/router.tsx` registrations + `router.test.ts` updates
- [ ] OpenAPI regen: `pnpm run generate-api` (backend running) — unblocks strict typing [VERIFIED: `package.json`]
- [ ] Playwright specs location: confirm under `web/e2e` per existing layout [ASSUMED — list_dir not run; `playwright.config.ts` exists [VERIFIED: glob]]

## Security Domain

Applicable **OWASP ASVS v4** style controls (project `security_asvs_level: 1` [VERIFIED: `.planning/config.json`]) for this phase:

| ASVS area | Applies | Standard control |
|-----------|---------|------------------|
| V2 Authentication | yes | `requireAuth` on dashboard routes; session cookies + CSRF headers on mutating calls [VERIFIED: `router.tsx`, `client.ts`] |
| V3 Session management | yes (reuse) | Same-session model as rest of portal [ASSUMED] |
| V4 Access control | yes | Rely on server 403/404; **do not** render contributor controls for non-owners [VERIFIED: REQUIREMENTS contributor matrix] |
| V5 Input validation | yes | Surface Spring validation messages; slug regex enforced server-side [VERIFIED: `SkillCollectionCreateRequest.java`] |
| V7 — Information leakage | **critical** | **D-08**, **VIS-03**, non-leaking counts **D-09** [VERIFIED: CONTEXT] |

| Pattern | STRIDE | Mitigation |
|---------|--------|------------|
| Enumeration of private collections | Information disclosure | Generic Not Found UI; no “private” wording for strangers [VERIFIED: CONTEXT **D-08**] |
| Skill metadata leakage in public collection | Information disclosure | Trust server-filtered members; avoid extra client calls that bypass visibility [VERIFIED: `listVisibleMembers`] |
| CSRF on POST/PATCH/DELETE | Tampering | `getCsrfHeaders` / `ensureCsrfHeaders` [VERIFIED: `client.ts`] |

## Sources

### Primary (HIGH confidence)

- `server/skillhub-app/.../SkillCollectionController.java` — REST map + DTO class names
- `server/skillhub-app/.../PublicSkillCollectionController.java` — public GET contract
- `server/skillhub-app/.../SkillCollectionPortalQueryAppService.java` — visibility filtering + public read behavior
- `server/skillhub-domain/.../SkillCollectionMembershipService.java` — reorder invariants
- `server/skillhub-auth/.../RouteSecurityPolicyRegistry.java` — anonymous vs authenticated route policy
- `web/src/app/router.tsx`, `web/src/api/client.ts`, `web/package.json`, `Makefile`

### Secondary (MEDIUM confidence)

- `web/src/api/generated/schema.d.ts` — indicative OpenAPI snapshot (staleness risk)

### Tertiary (LOW confidence)

- Vitest CLI filtering flags exactly as `--run` vs `run` — confirm in local Vitest 3 help [ASSUMED]

## Metadata

**Confidence breakdown:**

- Standard stack: **HIGH** — pinned in `package.json`
- Architecture: **HIGH** — matches existing router/hooks
- Pitfalls (public viewer, reorder, counts): **HIGH** — derived directly from Java sources
- OpenAPI currency: **MEDIUM** — grep suggests drift

**Research date:** 2026-04-15  
**Valid until:** ~2026-05-15 or first collections API DTO change (whichever first)
