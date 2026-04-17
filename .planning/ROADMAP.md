# Roadmap: SkillHub — Skill Collections (milestone v1.0)

## Overview

Deliver **user-owned skill collections** with **owner/contributor** collaboration, **public/private** visibility, strict **visibility-safe membership**, and **Web UI** management—backed by new persistence and APIs integrated into existing Spring/React/security patterns.

**Milestone acceptance:** Per-phase UAT for phase 04 is deferred; a **single milestone acceptance** (including collections QA/E2E/doc smoke from the phase-04 checklist) runs **after phases 05–06** complete. See `.planning/phases/04-verification-docs/04-UAT.md`.

## Phases

- **Phase 1: Domain & persistence** — Collections aggregate, collaborators, membership ordering; Flyway; repositories; domain services with authorization matrix groundwork.
- **Phase 2: Application & API** — App services, REST controllers (`/api/web/...` primary), `RouteSecurityPolicyRegistry` updates, admin parity, integrity rules **INT-***, audit alignment **ADM-01**.
- **Phase 3: Web UI** — Dashboard surfaces **WEB-*** flows; public collection page respects **VIS-***.
- **Phase 4: Verification & docs** — Automated + E2E tests **QA-***; short developer/user documentation.
- **Phase 5: Переработать UX добавления скиллов в коллекцию (добавление любых видимых скиллов)** — UX flow updates so users can add any skill they can view.
- **Phase 6: Переработать цветовую схему с AI Slop на нормальную** — Refresh visual theme to a production-ready color system.

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

- 01-01-PLAN.md — Flyway V40+ three-table schema, JPA entities, repository ports + infra JPA adapters; blocking `make test-backend`
- 01-02-PLAN.md — Domain services (CRUD, membership, contributors), policy matrix, caps, slug collisions, mocked `SkillReadableForActorPort` tests

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

- `02-01-PLAN.md` — DTOs + portal app services + `SkillCollectionController` / `PublicSkillCollectionController`
- `02-02-PLAN.md` — `RouteSecurityPolicyRegistry` + `RouteSecurityPolicyRegistryTest` + `SkillCollectionSecurityIT`
- `02-03-PLAN.md` — `reconcileInvisibleSkillsForCollection` + scheduler + **ADM-01** admin audit on commands

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

- 03-01: Routes + list/create/edit screens
- 03-02: Backend — D-09 aggregate + D-11 contributor reorder merge
- 03-03: Detail + contributor management + share + public page

### Phase 4: Verification & docs

**Goal:** Ship confidence—tests and minimal documentation.

**Depends on:** Phase 3

**Requirements:** QA-01..QA-03

**Success criteria:**

1. CI green including new tests.
2. E2E scenario documented and passing locally / CI.
3. API or user doc merged and linked from existing doc tree.

**Plans:** 3/3 plans complete

Plans:

- 04-01: Unit/integration matrix completion
- 04-02: Playwright (or agreed) E2E
- 04-03: Documentation pass

### Phase 5: Переработать UX добавления скиллов в коллекцию (добавление любых видимых скиллов)

**Goal:** Обновить UX потока добавления навыков в коллекцию так, чтобы owner/contributor/admin могли за один проход добавлять любые видимые им skills через inline-modal picker с full discovery controls и multi-select commit.
**Depends on:** Phase 4
**Requirements:** COL-04, ROL-04, INT-01, VIS-03, WEB-03, QA-02
**Plans:** 3/3 planned

Plans:

- 05-01-PLAN.md — Add-flow data layer: candidate discovery hook + bulk add orchestration + hook-level TDD
- 05-02-PLAN.md — Collection detail UX integration: Add skill modal, role gating, disabled already-in-collection, i18n
- 05-03-PLAN.md — Verification hardening: regression tests + E2E migration to real modal add-flow

### Phase 6: Переработать цветовую схему с AI Slop на нормальную

**Goal:** Перевести web UI на production-ready semantic color system (Enterprise Blue/Slate) с полной light/dark parity, system+manual theme toggle и удалением legacy AI-slop visual effects.
**Depends on:** Phase 5
**Requirements:** D-01..D-20 (см. `.planning/phases/06-color-scheme-refresh/06-CONTEXT.md`)
**Plans:** 3 plans

Plans:

- `06-01-PLAN.md` — Theme core foundation: semantic token contract, theme bootstrap, hardcoded-color policy gate
- `06-02-PLAN.md` — App shell + shared primitives migration, theme toggle UX, auth/dashboard semantic conversion
- `06-03-PLAN.md` — Feature hotspot sweep, light/dark parity matrix, strict completion hardening

---

*Roadmap created: 2026-04-15*

## Backlog

### Phase 999.2: Добавить в поиск поиск по коллекциям как first-class citizens (BACKLOG)

**Goal:** [Captured for future planning]
**Requirements:** TBD
**Plans:** 0 plans

Plans:

- TBD (promote with /gsd-review-backlog when ready)

### Phase 999.3: Добавить вход через Google OAuth (BACKLOG)

**Goal:** [Captured for future planning]
**Requirements:** TBD
**Plans:** 0 plans

Plans:

- TBD (promote with /gsd-review-backlog when ready)
