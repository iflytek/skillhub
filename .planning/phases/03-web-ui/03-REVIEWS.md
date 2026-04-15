---
phase: 3
reviewers:
  - agent-peer
reviewed_at: 2026-04-15T18:45:00+00:00
plans_reviewed:
  - .planning/phases/03-web-ui/03-01-PLAN.md
  - .planning/phases/03-web-ui/03-02-PLAN.md
  - .planning/phases/03-web-ui/03-03-PLAN.md
---

# Cross-AI Plan Review — Phase 3 (Web UI)

## Gemini Review

Not invoked (CLI availability not confirmed for this run).

---

## Claude Review

Not invoked (same model family as the orchestrating session; skipped for independence per GSD `review.md` guidance).

---

## Codex Review

**Could not be completed as a plan review.**

`codex exec --skip-git-repo-check -` was fed the full cross-AI prompt on stdin. The Codex session treated the request as implementation work: it explored repository files (e.g. `AddNamespaceMemberDialog`) and emitted tool-style output instead of a structured markdown plan assessment. No usable review artifact was produced.

**Recommendation for future runs:** constrain Codex with an explicit non-code role (e.g. system preamble: “You are a document reviewer only; do not use read_file or propose patches; output sections 1–5 in markdown”) or use a dedicated `codex exec` profile / model config for doc-only tasks.

---

## OpenCode Review

**Could not be completed.**

`opencode run` with attached plan files was started with review-only instructions; after several minutes the process had not written stdout content to the terminal capture (possible hang, auth, or provider stall). Re-run locally if an OpenCode review is required for parity with Phase 2.

---

## Agent peer review (adversarial plan pass)

### Summary

The three waves move from **client foundation** (OpenAPI, hooks, routes, CRUD UI, i18n) through **server hardening** (aggregate counts, safe partial reorder, tests) to **rich detail and public sharing** (skills, reorder UI, contributors, dual-mode public page). The sequence is mostly sensible—API contract and client scaffolding before public routes—but **03-03’s dual-fetch and enrichment story depends on 03-02’s security guarantees and stable response shapes**, and **OpenAPI drift** can silently desync 03-01 from the backend. **D-08 (“generic not found”)** is not explicitly owned by any single plan wave, leaving a **consistency gap** between public and authenticated error surfaces and possible **ID/slug enumeration** behavior.

### Strengths

- **Clear wave boundaries**: separating server-only reorder/count logic (03-02) from UI (03-01/03-03) reduces thrash and keeps security fixes testable without the full React surface.
- **Explicit security test intent** on 03-02 (IT + domain) for partial merge and “hidden member count never on public” aligns with the stated research risks.
- **Public vs authenticated GET** on the same URL in 03-03 matches real product needs (share links, progressive enhancement) if caching, auth, and error semantics are nailed down.
- **Router + i18n + tests called out in 03-01** reduces the chance of shipping untranslated strings and broken deep links.
- **Contributor section “mirroring namespace members”** suggests reuse of patterns and components, which can improve consistency and cut duplicate accessibility/i18n work.

### Concerns

- **HIGH:** **D-08 is unassigned** across the three plans. If public and authenticated endpoints return different 404 vs 403 behavior, or leak existence via timing/body differences, **03-03’s share flow becomes an enumeration oracle**. Without a single decision and tests, waves may implement incompatible handlers.
- **HIGH:** **Partial reorder merge “must not leak hidden ids”** is subtle: merging client order with server truth can **reveal cardinality or ordering hints** via response payloads, **ETags**, or **reordered positions** unless responses are carefully scrubbed and **integration tests cover contributor vs non-contributor** and **mixed visible/hidden** memberships.
- **MEDIUM:** **Dual-fetch for logged-in public viewers** (research): two round-trips on one page **worsens LCP**, complicates **loading/error states**, and risks **flicker or inconsistent snapshots** if the second response changes shape. **Cache invalidation** after mutations may double-hit the same endpoints.
- **MEDIUM:** **OpenAPI may be stale**: regenerating in 03-01 without a **contract lock** (generated artifacts committed + CI diff check, or server-first publish step) can ship **client types that lie about nullability, fields, and error models**, causing production runtime errors only on edge paths (public vs authed).
- **MEDIUM:** **N skill-by-id enrichment** in 03-03: without **batching**, **deduplication**, **parallel limits**, or **server-side aggregate `skills[]`**, the detail page can devolve into **waterfall requests** and **poor mobile performance**; error partials (some skills 404/forbidden) need UX and i18n.
- **MEDIUM:** **Test gaps**: 03-01 lists hooks/router tests but **03-03’s public/authenticated matrix**, **reorder UI**, **share actions**, and **VIS** may lack **e2e or contract tests**; **03-02 ITs** may not assert **OpenAPI-documented** behavior if the spec lags.
- **MEDIUM:** **i18n scope creep**: list/create/edit + dashboard + user menu + contributors + share strings across **public and authed** contexts multiply **plural/gender/casing** edge cases; **403 vs “not found” copy** must not contradict D-08.
- **LOW:** **Routing collisions**: `/u/$ownerKey/c/$collectionSlug` vs dashboard routes under `/collections` can duplicate logic; **canonical URLs**, **redirects**, and **SEO/meta** for public pages need a single source of truth.
- **LOW:** **Dependency order**: if **03-01 ships UI** that assumes **03-02 response fields** (e.g., hidden count only for contributors), feature flags or **version skew** between deploys can cause **undefined field** bugs unless optional chaining and API versioning are explicit.

### Suggestions

- **Own D-08 in writing**: pick **uniform 404** vs **403** policy per resource class, document **response body shape**, and add **cross-cutting tests** that run against both **public and authed** routes (including **non-member contributor** and **logged-out owner** cases).
- **Define the partial-reorder contract**: specify **idempotency**, **conflict behavior** (optimistic version, **412/409**), **maximum payload**, and **exact server merge rules** with **property-based or table-driven tests** for hidden members.
- **Collapse dual-fetch where possible**: prefer **one endpoint** with documented **viewer-specific fields**, or **a single BFF query**; if two calls remain, specify **dedupe**, **React Query** policy, and **loading UI** to avoid layout shift.
- **Batch skill enrichment**: add **`GET /skills?ids=`** or embed **skill summaries** in collection detail responses; cap **concurrency** and define **partial failure** rendering.
- **OpenAPI hygiene**: make **regen deterministic**, fail CI on drift, and **sequence merges** so server changes land before client regen PRs (or use **pinned spec artifact**).
- **i18n checklist**: **error codes** mapped to messages, **aria labels** for reorder/share, and **RTL** checks if supported elsewhere in the app.
- **Performance budgets**: set **max requests** and **p95 latency** targets for collection detail (public and authed) before VIS sign-off.

### Risk Assessment

**HIGH** — The combination of **public share URLs with authenticated upgrades**, **partial reorder merges that must hide membership details**, and **unowned D-08 error semantics** creates a **high-impact security and correctness surface**: small mistakes become **information leaks** or **broken authorizations**, while **OpenAPI drift** and **N+1 skill fetches** make **03-03** likely to ship **subtle production bugs** that unit tests on isolated pieces will miss. The plan is **directionally strong** but **needs explicit cross-wave contracts and tests** (especially **public vs authed parity** and **merge invariants**) to bring risk down to medium.

---

## Consensus Summary

External CLIs did not yield additional independent markdown reviews in this environment. The **agent peer review** aligns with **03-RESEARCH.md**: dual-fetch correctness, D-09/D-11 server contracts, and OpenAPI staleness remain the highest-leverage risks.

### Agreed strengths (with research / plans)

- Wave **03-02** correctly front-loads **D-09** and **D-11** before **03-03** consumes those contracts in the UI.
- Plans reference **namespace-members** patterns and **RouteSecurityPolicyRegistry**, matching repo conventions.
- Threat-model sections in each plan surface ASVS-style thinking.

### Agreed concerns (prioritize for `/gsd-plan-phase 3 --reviews`)

1. **D-08 and error parity** — Explicitly assign generic Not Found behavior and tests across public + authenticated flows (**HIGH**).
2. **Partial reorder merge semantics** — Lock idempotency, conflict behavior, and proof that responses cannot leak hidden membership (**HIGH**).
3. **Dual-fetch UX and consistency** — Specify loading states, deduplication, and snapshot rules for public-then-authenticated refetch (**MEDIUM**).
4. **Skill enrichment performance** — Avoid unbounded per-skill calls; document batching or server-side expansion (**MEDIUM**).
5. **OpenAPI / contract drift** — CI or workflow guard so 03-01 does not ship against a stale spec (**MEDIUM**).

### Divergent views

- **Codex vs plan intent:** The automated Codex session diverged into code exploration; this does **not** contradict the plans but shows **tooling risk** when using general-purpose coding agents for doc-only review.

---

## Next steps

- Incorporate feedback when re-planning or tightening tasks: **`/gsd-plan-phase 3 --reviews`**
- Optionally re-run **`/gsd-review --phase 3 --all`** after fixing Codex/OpenCode invocation (doc-only prompts, timeouts) for true multi-vendor consensus.
