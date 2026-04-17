# Phase 5: UX Add Visible Skills — Research

**Researched:** 2026-04-16  
**Domain:** React collection detail UX + existing collection membership APIs  
**Confidence:** HIGH (core files and contracts verified in repo)

<user_constraints>

## Locked Decisions From Phase Context

- Add entry point must be a visible `Add skill` action near the skills section title.
- Empty collection state must use the same add-flow (not a separate `Browse my skills` path).
- Add action must be available to owner, contributor, and admin-equivalent actors.
- Picker must be inline modal and support search, filter, sort, pagination.
- Picker selection mode is multi-select with explicit `Add selected (N)`.
- Existing members remain visible in picker but disabled with `Already in collection`.
- Candidate cards must show `displayName`, `namespace`, `status/visibility`, and truncated `summary`.

</user_constraints>

## Verified Current State

### Collection Detail UX Baseline

- `web/src/pages/dashboard/collection-detail.tsx` currently has no modal add-flow and no `Add skill` action near `Skills`.
- Empty skills state currently routes to `/dashboard/skills` via `collections.emptySkillsCta`.
- Role derivation (`isOwner`, `isContributor`, admin-equivalent) is already present and reusable for add-action gating.

### Existing Collection Data Contracts

- `web/src/shared/hooks/use-collection-queries.ts` already provides:
  - `useCollectionDetail`
  - `useAddCollectionSkill`
  - `useRemoveCollectionSkill`
  - `useReorderCollectionSkills`
- `collectionApi.addSkill` is single-item POST: `POST /api/web/collections/{id}/skills`.
- Backend enforces addability and duplicates:
  - `COL-04`/`ROL-04` via visibility check
  - `INT-01`/duplicate guard via domain errors

### Search/Discovery Building Blocks

- `web/src/shared/hooks/use-skill-queries.ts` + `buildSkillSearchUrl` already support:
  - search query
  - label filter
  - sort
  - pagination
- Search endpoint remains visibility-safe for acting user (correct source for picker candidates).

### Rendering Assets

- `web/src/features/skill/skill-card.tsx` defines compact card pattern with summary clamp and namespace rendering.
- `web/src/pages/search.tsx` is the reference interaction model for search controls and pagination behavior.
- `web/src/i18n/locales/en.json` and `zh.json` already contain collection namespace keys; new add-flow keys can be appended.

## Gaps To Close In This Phase

1. No reusable candidate-query hook tied to collection context and existing member IDs.
2. No batch add orchestration for multi-select UX (current API is single add only).
3. No modal-based picker UI on collection detail.
4. E2E currently adds a skill through direct API request instead of user-facing add-flow.

## Recommended Implementation Shape

### Plan 01: Data Layer

- Add `useCollectionAddCandidates` hook that composes existing skill search primitives and marks `alreadyInCollection`.
- Add `useBulkAddCollectionSkills` mutation that executes existing single-add API per selected skill and classifies outcomes.

### Plan 02: UI Flow

- Build inline `AddCollectionSkillsDialog` component.
- Wire trigger into collection detail header and empty state.
- Implement selection UX, disabled already-added rows, and in-modal empty state guidance.

### Plan 03: Confidence

- Convert E2E to add via modal interaction.
- Add regression tests for duplicate and partial batch outcomes.

## Risk Register

| Risk | Impact | Mitigation |
|------|--------|------------|
| Candidate source bypasses visibility checks | Metadata leak (HIGH) | Use existing search endpoint + query hooks only |
| Multi-select creates duplicate churn | UX noise, avoidable errors | Pre-disable already-added + classify duplicate server errors |
| Role-gating mismatch in detail page | Unauthorized action visibility | Reuse current owner/contributor/admin checks in `collection-detail.tsx` |
| E2E remains API-seeded | False confidence | Move happy-path add step to UI modal interaction |

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Vitest (unit/component), Playwright (E2E) |
| Quick command | `cd web && pnpm run test -- src/shared/hooks/use-collection-queries.test.ts` |
| UI flow command | `cd web && pnpm run test -- src/features/collection/add-collection-skills-dialog.test.tsx` |
| E2E command | `cd web && pnpm run test:e2e -- e2e/collections-flow.spec.ts` |
| Static checks | `cd web && pnpm run typecheck` |

### Requirement Mapping

| Requirement | Planned evidence |
|-------------|------------------|
| COL-04 / ROL-04 | Batch add uses existing server add endpoint; unauthorized adds rejected server-side |
| INT-01 | Error classification and user feedback on failed add attempts |
| VIS-03 | Candidate source and public-page assertions remain visibility-safe |
| WEB-03 | Collection detail gains complete add-flow entry and interaction model |
| QA-02 | E2E validates user-level add flow (not API shortcut) |

---

*Phase: 05-ux-add-visible-skills*  
*Research completed: 2026-04-16*
