# Requirements: SkillHub — Skill Collections (milestone v1.0)

**Defined:** 2026-04-15  
**Core value (from PROJECT.md):** Teams govern, publish, and reuse agent skills in one place.  
**Research:** `docs/2026-04-15-skill-collections-research.md`, `.planning/research/SUMMARY.md`

## MVP product decisions (closes research §5)

These are **binding for v1** unless explicitly revised in this file:

| Topic | Decision |
| ----- | -------- |
| Contributor powers | May **add** and **remove** skills, and **reorder** items. May **not** rename collection, edit description, change visibility, delete collection, or **add/remove contributors**. |
| Owner powers | Full metadata, visibility, delete, contributor management, reorder, add/remove skills. |
| Platform admins | Same effective capabilities as owner for support (**technical full access**). No separate moderation workflow in v1. |
| Collection scope | **User-scoped** (owned by a user id). Namespace-only audience **deferred**. |
| Visibility modes | **`public`** and **`private`** only. **Unlisted** deferred. |
| Public vs private skills in a public collection | Anonymous and logged-in viewers see **only skills they are allowed to see**; collection shell (title/description/slug) visible per visibility mode. **No** “hide entire collection if any member is private”. |
| Who is checked on “Add skill” | The **acting user** must pass the same visibility/read rules used elsewhere in the portal for that skill. |
| Owner adds skill invisible to a contributor | **Allowed** if owner can see the skill; contributor’s UI **filters** items they cannot resolve—show count or omit silently per UX choice in implementation plan (must not leak metadata). |
| Skill lifecycle | If a skill becomes **no longer listable** for a user, it **disappears** from their view; system **eventually removes** stale rows (see **INT-03**). |
| i18n | Single stored **title** and **description** string; no per-locale columns in v1. |
| Notifications | **Out of scope** v1. |
| Limits | Configurable constants: default cap **100** skills per collection, **50** collections per owner, **20** contributors per collection (tunable before ship). |

---

## v1 Requirements

### Collections core (COL)

- [x] **COL-01**: Owner can create a collection with title, optional description, and visibility `public` or `private`.
- [x] **COL-02**: Owner can edit title, description, and visibility of their collection.
- [x] **COL-03**: Owner can delete a collection; deletion removes all memberships and collaborator rows.
- [x] **COL-04**: Owner can add a skill to a collection only if the server validates the skill is **readable by the acting user** under existing SkillHub visibility rules.
- [x] **COL-05**: Owner can remove a skill from a collection.
- [x] **COL-06**: Owner can reorder skills in a collection (stable ordering persisted).
- [x] **COL-07**: Collection has a stable **share identifier** for URLs (e.g. owner slug + collection slug or opaque id—implementation plan picks one pattern consistent with routing).

### Roles & collaboration (ROL)

- [x] **ROL-01**: Each collection has exactly **one** owner (`owner_id`); creator becomes owner.
- [x] **ROL-02**: Owner can grant **contributor** role to another **registered** user via UI (reuse or mirror patterns from namespace member / user lookup where applicable).
- [x] **ROL-03**: Owner can revoke contributor access.
- [x] **ROL-04**: Contributor can add a skill subject to the same validation as **COL-04**.
- [x] **ROL-05**: Contributor can remove a skill from the collection.
- [x] **ROL-06**: Contributor can reorder skills (**same as owner** for membership ordering only).
- [x] **ROL-07**: Contributor **cannot** perform metadata edits, visibility changes, collection delete, or contributor management (server-enforced).
- [x] **ROL-08**: Platform roles with global admin privileges (**as already defined in SkillHub**) can perform owner-equivalent operations for support.

### Visibility & discovery (VIS)

- [x] **VIS-01**: `private` collections are visible in list/detail **only** to owner, contributors, and platform admins.
- [x] **VIS-02**: `public` collection **metadata** is readable per route policy (including anonymous read if product allows public catalog pages—**default:** match existing public skill catalog stance: anonymous may read **public** collection metadata).
- [x] **VIS-03**: For `public` collections, **skill member list** returns only skills the **current viewer** is allowed to see; no leakage of private skill fields.

### Web experience (WEB)

- [ ] **WEB-01**: Authenticated user can open a **“My collections”** area from the dashboard (or equivalent navigation) listing collections they own or contribute to.
- [ ] **WEB-02**: User can create and edit their collections (forms + validation errors surfaced).
- [ ] **WEB-03**: User can open collection detail showing ordered skills with links to existing skill detail pages.
- [ ] **WEB-04**: User can copy/share a **canonical URL** to a public collection; private collections require auth and permission.
- [ ] **WEB-05**: Owner can manage contributors (list, add, remove) in UI.

### Integrity & lifecycle (INT)

- [x] **INT-01**: Server rejects add-skill requests with a clear error when the acting user cannot read the skill.
- [x] **INT-02**: Duplicate membership of the same skill in one collection is impossible (unique constraint).
- [x] **INT-03**: When a skill becomes inactive/invisible to everyone who matters, membership rows are **removed or reconciled** by a defined strategy (job on read, periodic task, or domain event—implementation plan chooses; user-visible result: item no longer appears).

### Governance & quality (GOV / QA)

- [x] **ADM-01**: Admin override actions are audited if SkillHub already has audit patterns for comparable mutations; if none, log at minimum at service level (**implementation plan** aligns with existing audit module).
- [x] **QA-01**: Automated tests cover authorization matrix (owner / contributor / stranger / admin) for representative endpoints.
- [ ] **QA-02**: Playwright or agreed E2E covers: create collection → add skill → public view filter → contributor add/remove.
- [ ] **QA-03**: Developer-facing note added to `document/` or in-repo docs describing new endpoints for Web (OpenAPI/Swagger already generated if applicable).

---

## v2 Requirements (deferred)

- **VIS-U01**: `unlisted` visibility (share link only, not listed in public gallery).
- **NSC-01**: Namespace-scoped collections or “visible to namespace members only”.
- **SRCH-01**: Index collections in `skillhub-search` and surface in global search.
- **TOK-01**: API-token scopes and CLI management for collections.
- **NOTF-01**: Notifications for invites, removals, membership changes.
- **MOD-01**: Moderation workflows / admin screens dedicated to collections.

---

## Out of Scope

| Item | Reason |
| ---- | ------ |
| Reusing `skill_tag` / `SkillLabel` / `skill_star` as collections | Different product semantics; research explicit |
| Token-based automation for collections | Web-only MVP |
| Collection-level comments / ratings | Not requested |
| Transferring collection ownership | Not in v1; add in v2 if needed |

---

## Traceability

| Requirement | Phase | Status |
| ----------- | ----- | ------ |
| COL-01 — COL-07 | Phase 1 | Pending |
| ROL-01 — ROL-08 | Phase 1 | Pending |
| VIS-01 — VIS-03 | Phase 2 | Pending |
| INT-01 — INT-03 | Phase 2 | Complete |
| WEB-01 — WEB-05 | Phase 3 | Pending |
| ADM-01 | Phase 2 | Complete |
| QA-01 — QA-03 | Phase 4 | Pending |

**Coverage:**

- v1 requirements: **27** total  
- Mapped to phases: **27**  
- Unmapped: **0** ✓

---
*Requirements defined: 2026-04-15*  
*Last updated: 2026-04-15 after `/gsd-new-milestone`*
