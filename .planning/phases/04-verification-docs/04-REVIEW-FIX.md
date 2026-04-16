---
phase: "04-verification-docs"
fixed_at: "2026-04-17T00:00:00Z"
review_path: ".planning/phases/04-verification-docs/04-REVIEW-UNIFIED.md"
iteration: 1
findings_in_scope: 7
fixed: 7
skipped: 0
status: all_fixed
---

# Phase 04: Code review fix report (unified)

**Source reviews:** `04-REVIEW-UNIFIED.md`, `04-REVIEW.md` (items below applied per user scope; U-M and IN-002 intentionally out of scope).

**Summary**

- Documented authenticated collections list route as `GET /api/web/me/collections`.
- Documented public collection reads as `GET /api/web/public/collections/{ownerId}/{slug}` and `GET /api/v1/public/collections/{ownerId}/{slug}` (ZH + EN API docs).
- Playwright `collections-flow`: `TestInfo` passed into `createFreshSession` for the secondary browser context.
- Public collection not-found UI: stable `data-testid="public-collection-not-found"`.
- E2E guard spec asserts that test id instead of an English-only heading role.
- `docs/e2e.md`: directory overview and spec count aligned with 32 `web/e2e/*.spec.ts` files; partial list disclaimer.
- New `document/README.md` explaining the webpack `5.94.0` override for Docusaurus build compatibility.

## Fixed items

### 1. API docs (collections routes)

**Files:** `document/docs/04-developer/api/authenticated.md`, `document/i18n/en/docusaurus-plugin-content-docs/current/04-developer/api/authenticated.md`

**Applied fix:** Core web routes HTTP block updated for me-scoped list and public read endpoints (Web + JSON), with comments adjusted.

### 2. Playwright secondary session

**File:** `web/e2e/collections-flow.spec.ts`

**Applied fix:** Import `TestInfo`; `newSecondarySession(browser, page, testInfo)`; `createFreshSession(secondaryPage, testInfo)`.

### 3. Stable not-found selector

**File:** `web/src/pages/public/public-collection-page.tsx`

**Applied fix:** `data-testid="public-collection-not-found"` on the not-found `Card` for `collections.publicNotFoundTitle`.

### 4. E2E guard spec

**File:** `web/e2e/collections-visibility-guard.spec.ts`

**Applied fix:** Assert `getByTestId('public-collection-not-found')` instead of `getByRole('heading', { name: 'Collection not found' })`.

### 5. E2E runbook

**File:** `docs/e2e.md`

**Applied fix:** Section 2 documents `web/e2e/*.spec.ts` (32 files) + `helpers/`; section 3 states 32 spec files and points to the glob for the full list.

### 6. Webpack override documentation

**File:** `document/README.md` (new)

**Applied fix:** Short section on `overrides.webpack` `5.94.0` and when to revisit.

### 7. Skipped by instruction

**Not changed:** U-M (tooling meta), IN-002 (SkillCollectionSecurityIT) — per request.

---

_Fixed: 2026-04-17_
