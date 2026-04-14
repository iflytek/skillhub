# Research summary — Skill Collections (milestone v1.0)

**Inputs:** `docs/2026-04-15-skill-collections-research.md`, existing SkillHub modules (domain skill/social/label, `RouteSecurityPolicyRegistry`, web router).

## Stack / integration

- Additive **Flyway** migrations; new JPA entities in `skillhub-domain`; orchestration + REST in `skillhub-app`; **session-first** route policies in `skillhub-auth` for new `/api/web/...` collection endpoints.
- Reuse **visibility and skill resolution** from existing services when validating “can add skill” and when rendering public collection membership.

## Feature table stakes

- CRUD collections (metadata + visibility public/private).
- Ordered membership of skills; owner + contributors; admins override.
- Web list/detail/edit + share entry for public collections.
- Hard rule: **reject** add when skill is not allowed for the **acting user** under current portal rules (aligns with “no private skills in collection” product intent).

## Architecture notes

- New aggregate **Collection** + **CollectionMember** (skills) + **CollectionCollaborator** (contributors)—exact table names TBD in implementation plan.
- Do **not** overload `skill_tag`, `SkillLabel`, or `SkillStar`.

## Pitfalls / watch

- **Permission lattice:** define contributor vs owner in writing before coding (done in REQUIREMENTS.md).
- **Public page:** never expose private skill titles/summaries to anonymous users—filter members per viewer.
- **Stale membership:** when skill archived/deleted/hidden—define prune vs ghost rows (see `INT-*` requirements).
- **Invitation UX:** depend on existing user directory / search patterns used elsewhere (namespaces, admin users).

## Deferred (explicit)

- Search indexing for collections, notifications, unlisted mode, namespace-scoped audience, API-token scopes.
