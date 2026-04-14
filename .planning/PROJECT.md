# SkillHub — GSD project context

## What This Is

SkillHub is a self-hosted, enterprise-oriented **agent skill registry**: teams publish versioned skill packages into namespaces, discover them via search, and install or integrate using APIs and CLI-compatible flows. This repository contains the Java/Spring backend modules, React web app, search pipeline, and documentation.

## Core Value

Teams can **govern, publish, and reuse** agent skills in one place—with visibility, review, and discovery—without giving up data sovereignty.

## Current Milestone: v1.0 Skill Collections (GSD)

**Goal:** Let users **group skills into named collections** (by role or task), **share** them with others, and **collaborate** with a clear owner vs contributor model—without overloading `skill_tag`, admin `label_*`, or `skill_star`.

**Target features:**

- User-owned **collections** with title, description, and **public / private** visibility
- **Owner** (single, full control) and **contributors** (add/remove/reorder skills only)
- **Platform administrators** retain full governance access
- **Invariant:** a skill that is not addable under existing portal visibility rules **cannot** be placed in a collection
- **MVP channel:** Web UI only (session auth); no new API-token scopes for collections in this milestone

**Source research:** `docs/2026-04-15-skill-collections-research.md`

## Requirements

### Validated

(None tracked here yet — populate when features ship.)

### Active

- [ ] Collections domain model, persistence, and visibility rules (`COL-*`, `VIS-*`, `ROL-*`)
- [ ] Backend APIs + web security alignment for collection CRUD and membership
- [ ] Web UI: list, create/edit, detail, sharing entry points, contributor management (owner)
- [ ] Lifecycle handling for collection items when skills become unavailable (`INT-*`)
- [ ] Verification: automated tests + critical path manual / E2E notes (`QA-*`)

### Out of Scope (this milestone)

- **Unlisted** visibility mode (link-only without listing) — defer
- **Namespace-scoped** collections (visible only to namespace members) — defer; v1 is **user-scoped** collections
- **Collection search** in global search index — defer
- **CLI / long-lived API token** scopes for managing collections — defer (Web UI only)
- **In-app / email notifications** for collection events — defer
- **Dedicated admin moderation UI** for collections — defer; admins keep technical full access per `ADM-*`, product screens optional later
- **i18n-specific DB fields** for collection title/description — v1 uses a single stored string; UI locale handled at display time only

## Context

- Backend: Java 21, Spring, JPA, Flyway (`server/skillhub-*`).
- Frontend: React 19, TanStack Router (`web/`).
- Existing social pattern: `SkillStar` (per-user, per-skill); collections are a **separate aggregate**.
- Label system (`docs/2026-03-20-skill-label-system-design.md`) is **admin curation**, not user collections.

## Constraints

- **Tech:** Reuse existing auth (`PlatformPrincipal`, session + role guards), `ApiResponse` envelope, and visibility services (`SkillQueryService` / `VisibilityChecker`)—do not fork visibility logic.
- **Security:** New routes must be registered in `RouteSecurityPolicyRegistry` for session policy consistency (API token policies unchanged for this milestone).
- **Consistency:** Contributor boundary and public listing rules are defined in `REQUIREMENTS.md`; change only via explicit product decision.

## Key Decisions

| Decision | Rationale | Outcome |
| -------- | --------- | ------- |
| User-scoped collections for v1 | Faster delivery; namespace-only audience deferred | — Pending |
| Contributors cannot change metadata or membership | Reduces permission ambiguity; matches user workshop direction | — Pending |
| Public collections filter member skills per viewer | Avoids leaking private skill metadata | — Pending |
| Web UI only for MVP | User direction; defers token matrix | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):

1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):

1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-04-15 after `/gsd-new-milestone` — Skill Collections*
