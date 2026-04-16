# Roadmap: SkillHub — Skill Collections (milestone v1.0)

## Overview

Deliver **user-owned skill collections** with **owner/contributor** collaboration, **public/private** visibility, strict **visibility-safe membership**, and **Web UI** management—backed by new persistence and APIs integrated into existing Spring/React/security patterns.

## Phases

- [x] **Phase 1: Domain & persistence** — Collections aggregate, collaborators, membership ordering; Flyway; repositories; domain services with authorization matrix groundwork.
- [x] **Phase 2: Application & API** — App services, REST controllers (`/api/web/...` primary), `RouteSecurityPolicyRegistry` updates, admin parity, integrity rules **INT-***, audit alignment **ADM-01**.
- [x] **Phase 3: Web UI** — Dashboard surfaces **WEB-*** flows; public collection page respects **VIS-***.
- [ ] **Phase 4: Verification & docs** — Automated + E2E tests **QA-***; short developer/user documentation.

## Phase details

### Phase 1: Domain & persistence

**Goal:** Durable model and domain APIs for collections, members, collaborators; no UI.

**Depends on:** Nothing

**Requirements:** COL-01..COL-07, ROL-01..ROL-08 (persistence + domain validation; HTTP optional stub behind feature flag if needed)

**Success criteria:**

1. Schema migrations apply cleanly on empty and existing deployments.
2. Domain layer can create/update/delete collection with owner and enforce single-owner invariant.
3. Domain layer can add/remove/reorder skills with duplicate prevention and contributor rule enforcement at service level (unit tests).

**Plans:** 2 plans in `/gsd-plan-phase 1`

Plans:

- [x] 01-01-PLAN.md — Flyway V40+ three-table schema, JPA entities, repository ports + infra JPA adapters; blocking `make test-backend`
- [x] 01-02-PLAN.md — Domain services (CRUD, membership, contributors), policy matrix, caps, slug collisions, mocked `SkillReadableForActorPort` tests

### Phase 2: Application & API

**Goal:** Expose secure HTTP API for all v1 behaviors; align security policy.

**Depends on:** Phase 1

**Requirements:** VIS-01..VIS-03, INT-01..INT-03, ADM-01, remainder of COL/ROL exposed via API

**Success criteria:**

1. Integration tests cover owner/contributor/stranger/admin matrix for key routes.
2. Public collection API never returns forbidden skill payloads to anonymous users.
3. `RouteSecurityPolicyRegistry` documents new routes; session auth works for private collections.

**Plans:** 3/3 plans executed

Plans:

- [x] `02-01-PLAN.md` — DTOs + portal app services + `SkillCollectionController` / `PublicSkillCollectionController`
- [x] `02-02-PLAN.md` — `RouteSecurityPolicyRegistry` + `RouteSecurityPolicyRegistryTest` + `SkillCollectionSecurityIT`
- [x] `02-03-PLAN.md` — `reconcileInvisibleSkillsForCollection` + scheduler + **ADM-01** admin audit on commands

### Phase 3: Web UI

**Goal:** Users manage and share collections entirely through the web app.

**Depends on:** Phase 2

**Requirements:** WEB-01..WEB-05 (drives UX for prior API)

**Success criteria:**

1. Owner can complete happy path without CLI.
2. Contributor restrictions observable in UI (disabled controls + server errors as backstop).
3. Public collection page matches **VIS-** filtering rules.

**Plans:** 3 in `/gsd-plan-phase 3`

Plans:

- [x] 03-01: Routes + list/create/edit screens
- [x] 03-02: Backend — D-09 aggregate + D-11 contributor reorder merge
- [x] 03-03: Detail + contributor management + share + public page

### Phase 4: Verification & docs

**Goal:** Ship confidence—tests and minimal documentation.

**Depends on:** Phase 3

**Requirements:** QA-01..QA-03

**Success criteria:**

1. CI green including new tests.
2. E2E scenario documented and passing locally / CI.
3. API or user doc merged and linked from existing doc tree.

**Plans:** TBD in `/gsd-plan-phase 4`

Plans:

- [ ] 04-01: Unit/integration matrix completion
- [ ] 04-02: Playwright (or agreed) E2E
- [ ] 04-03: Documentation pass

---
*Roadmap created: 2026-04-15*

## Backlog

### Phase 999.1: Переработать UX добавления скиллов в коллекцию (добавление любых видимых скиллов) (BACKLOG)

**Goal:** [Captured for future planning]
**Requirements:** TBD
**Plans:** 0 plans

Plans:
- [ ] TBD (promote with /gsd-review-backlog when ready)
