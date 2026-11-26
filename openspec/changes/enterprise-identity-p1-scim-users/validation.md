# Validation plan

- `openspec validate enterprise-identity-p1-scim-users --strict`
- Backend focused tests for Directory Connection, SCIM resource mapping, membership lifecycle, authority version, replay and cross-organization guards.
- PostgreSQL 16 and 17 regression, migration guardrails and OpenAPI drift checks.
- Web tests, typecheck, lint and build for the Organization Directory Connection management states.
- Repeatable SCIM reference service or fixture: ServiceProviderConfig, Users create/list/filter/get/put/patch/deactivate/replay, invalid token, paused connection, cross-organization resource and request limits.
- Browser/manual evidence must show the organization management page and directory connection state; it must not claim that a SCIM API call proves OIDC provider login.
