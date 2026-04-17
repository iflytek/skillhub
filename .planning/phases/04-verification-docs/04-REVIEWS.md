---
phase: 4
reviewers: [codex, opencode]
reviewed_at: 2026-04-16T20:15:00Z
plans_reviewed: [04-01-PLAN.md, 04-02-PLAN.md, 04-03-PLAN.md]
---

# Cross-AI Plan Review — Phase 4

## Codex Review

**Run:** `codex exec --skip-git-repo-check` with phase-4 review prompt (~85s, exit 0). Model: `gpt-5.3-codex` per Codex banner.

### 04-01-PLAN.md

**Summary:** Targets QA-01 through `SkillCollectionSecurityIT` and `SkillCollectionDomainServiceTest`, exercising owner/contributor/stranger/admin on representative collection operations.

**Strengths:** Clear QA-01 mapping; integration plus domain layers; concrete Gradle test filters; “representative, not exhaustive” scope fits the phase brief.

**Concerns:** **HIGH:** INT-03 (skill becomes invisible; membership reconciliation) is not a named, explicit test scenario. **HIGH:** No formal route × role × expected-result matrix—unlisted “representative routes” can miss critical cases. **MEDIUM:** Per-role expected HTTP/domain outcomes are not written down. **LOW:** Definition of done is mostly “tests green” without measurable coverage targets.

**Suggestions:** Publish a small matrix table (endpoint, role, expected status/behavior); add an integration transition “visible → invisible” for INT-03; cap minimum matrix cases and require negative checks; assert platform admin never gains access where policy forbids it.

**Risk assessment:** **HIGH** until INT-03 and the matrix are explicit; should drop once those are fixed.

### 04-02-PLAN.md

**Summary:** Happy path (`collections-flow.spec.ts`) plus guard (`collections-visibility-guard.spec.ts`), reusing Playwright and CI hooks.

**Strengths:** Aligns with “one happy path + one guard”; covers create collection → add skill → public/filter → contributor management; names `pr-e2e.yml`.

**Concerns:** **HIGH:** INT-03 is not clearly exercised—no E2E where visibility changes and reconciliation is asserted. **MEDIUM:** Assertions for “public filter” and “forbidden” may stay vague (what must be hidden vs allowed). **MEDIUM:** Flakiness risk for share/public propagation not addressed (waits, deterministic seeds). **LOW:** “Update `pr-e2e.yml` if needed” is loose.

**Suggestions:** Add a guard path for “skill becomes invisible after membership change” with concrete assertions; document expected UI/API per step; document anti-flake rules (fixtures, selectors, wait policy); state which specs are mandatory in the PR gate.

**Risk assessment:** **MEDIUM–HIGH** without an explicit lifecycle/reconciliation scenario.

### 04-03-PLAN.md

**Summary:** Developer-facing authenticated API docs, English i18n mirror, and `docs/e2e.md` runbook for QA-03.

**Strengths:** Direct QA-03 linkage; i18n mirror called out; includes run commands.

**Concerns:** **MEDIUM:** `rg` proves substring presence, not navigation/link integrity or factual correctness. **MEDIUM:** “Endpoint notes” may still omit roles, auth behavior, and error cases. **LOW:** No explicit parity rule between primary and `en` sections.

**Suggestions:** Add a short doc checklist (endpoint, roles, visibility rules, status/error matrix, sample calls); validate links in the doc tree; add i18n parity criteria for mirrored headings.

**Risk assessment:** **MEDIUM**.

### Overall phase (Codex)

**Summary:** Plan ordering and dependencies are coherent (backend → E2E → docs). The largest cross-cutting gap is traceability for **INT-03** across verification artifacts.

**Strengths:** Decomposition tracks QA-01..QA-03; sensible sequencing; balanced integration, E2E, and documentation work.

**Concerns:** **HIGH:** INT-03 is not locked as mandatory acceptance across the three plans. **MEDIUM:** Weak explicit mapping from requirement → test/spec → evidence. **MEDIUM:** Per-plan exit criteria are not very measurable.

**Suggestions:** One traceability table (QA/INT requirement → artifact); minimum security/visibility assertion set; explicit phase exit gates for merge; if INT-03 is in scope for phase 4, mark it must-pass in 04-01 and/or 04-02.

**Risk assessment:** **HIGH** as written; expect **MEDIUM** after INT-03 and matrices are nailed down.

---

## OpenCode Review

### 04-01-PLAN.md (QA-01: Backend authorization matrix)

**Summary:** Focused plan to extend integration and domain tests with owner/contributor/stranger/admin matrix on representative high-risk collection routes. Scope is appropriately bounded to representative confidence, not exhaustive permutations.

**Strengths**

- Clear Gradle verification commands with specific test filters.
- Anchors on existing tests (`SkillCollectionSecurityIT`, `SkillCollectionDomainServiceTest`).
- Covers mutations and sensitive reads; domain tests for contributor boundaries and admin override.

**Concerns**

- **MEDIUM:** “Representative routes” is not an explicit endpoint list; a critical route could be omitted.
- **LOW:** `files_modified` lists `SkillCollectionControllerIT` but tasks center on SecurityIT—clarify whether controller IT is in scope.
- **LOW:** INT-03 lifecycle is not called out in QA-01 scope (may be intentional if deferred).

**Suggestions**

- Add an explicit HTTP route/method matrix in the plan (or in SUMMARY) for auditability.
- State whether `SkillCollectionControllerIT` must change or is reference-only.

**Risk:** **LOW** for QA-01 if the route list gap is closed during execution.

---

### 04-02-PLAN.md (QA-02: Playwright E2E)

**Summary:** Two specs—happy path and visibility/permission guard—wired into existing Playwright and CI; depends on 04-01, which is appropriate.

**Strengths**

- Clear scenario steps (create → add skill → share/public → contributor management).
- Guard spec targets non-leak expectations.
- References existing E2E conventions and `auth-fixtures.ts`.

**Concerns**

- **MEDIUM:** Skill/fixture seeding for “add skill” is not spelled out; risk of flaky or blocked E2E.
- **MEDIUM:** Autonomous handoff 04-01 → 04-02 lacks an explicit “ready” signal (e.g. summary artifact or checklist).
- **LOW:** Guard scenario could mean several different threats (private skill in public collection vs contributor vs stranger); narrow the acceptance criteria in the plan text.

**Suggestions**

- Document which seeded skills/users each spec assumes and how CI provides them.
- Add an explicit dependency gate (e.g. require `04-01-SUMMARY.md` or green job) before starting 04-02.

**Risk:** **MEDIUM** until fixtures and guard semantics are explicit.

---

### 04-03-PLAN.md (QA-03: Documentation)

**Summary:** Updates developer API docs and `docs/e2e.md`, with i18n mirror; depends on 04-01 and 04-02 (wave 2).

**Strengths**

- Concrete paths; verification via `rg` for required strings.
- Linked doc tree and E2E commands aligned with repo layout.

**Concerns**

- **MEDIUM:** Docs depend on both prior plans—confirm 04-02 artifacts exist before writing endpoint lists that mirror reality.
- **LOW:** If OpenAPI is generated, clarify manual doc vs “verify generated spec includes collections routes.”
- **LOW:** i18n mirror is named; other locales may need the same section or an explicit “English canonical” note.

**Suggestions**

- Add dependency checks (both SUMMARY files or equivalent) before 04-03 execution.
- One line on whether OpenAPI regen is part of QA-03 or out of scope.

**Risk:** **LOW**.

---

### Overall (OpenCode)

Dependency flow 04-01 → 04-02 and (04-01 + 04-02) → 04-03 is sound. Cross-cutting gaps: fixture story for 04-02, explicit route matrix for 04-01, INT-03 boundary documented if not verified in phase 4.

**Overall phase risk (OpenCode):** **LOW to MEDIUM** — execution risk is mostly measurability (routes, seeds, guard meaning), not direction.

---

## Consensus Summary

### Agreed strengths

- Three-plan split (backend matrix → E2E → docs) matches roadmap and QA-01..QA-03.
- Reuse of existing Gradle filters, Playwright layout, and Docusaurus doc paths is pragmatic.
- Dependencies between plans are coherent.

### Agreed concerns

- **Representative** QA-01 coverage needs a concrete route/role checklist or equivalent evidence in SUMMARY.
- **E2E reliability** hinges on explicit data/fixture and cleanup assumptions.
- **INT-03** is underspecified across plans: both reviewers flag ambiguity (in scope vs explicitly deferred vs must-verify).
- **Traceability:** requirement → test/spec → evidence mapping should be tighter (Codex); OpenCode adds fixture and guard-semantics clarity for 04-02.

### Divergent views

- **Overall phase risk:** OpenCode **LOW–MEDIUM** (execution measurability: routes, seeds, guard meaning); Codex **HIGH** until INT-03 and formal matrices are explicit—then **MEDIUM**. Resolve by deciding whether INT-03 is a phase-4 gate and encoding that in plans and SUMMARYs.
- OpenCode also stressed payload-level non-leak assertions and docs link/build checks beyond `rg` string presence.

### Practical follow-ups

1. Decide and document **INT-03** for phase 4 (must-verify with named tests vs deferred with rationale in `04-CONTEXT` / ROADMAP).
2. When executing plans, add endpoint matrix + fixture notes to `04-01-SUMMARY.md` / `04-02-SUMMARY.md` so QA-03 and E2E stay accurate.
