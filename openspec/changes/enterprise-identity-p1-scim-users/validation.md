# Validation plan

- `openspec validate enterprise-identity-p1-scim-users --strict`
- Backend focused tests for Directory Connection, SCIM resource mapping, membership lifecycle, authority version, replay and cross-organization guards.
- PostgreSQL 16 and 17 regression, migration guardrails and OpenAPI drift checks.
- Web tests, typecheck, lint and build for the Organization Directory Connection management states.
- Repeatable SCIM reference service or fixture: ServiceProviderConfig, Users create/list/filter/get/put/patch/deactivate/replay, invalid token, paused connection, cross-organization resource and request limits.
- `scripts/scim-user-lifecycle-fixture.sh` is the repeatable client fixture for the local lifecycle run; it requires an explicitly created test Directory Connection and never prints the bearer token.
- `If-Match`/`ETag` checks cover stale replacement rejection; configured request and field limits are covered by service tests and deployment defaults.
- Browser/manual evidence must show the organization management page and directory connection state; it must not claim that a SCIM API call proves OIDC provider login.

## 2026-11-27 isolated acceptance evidence

- Environment: PostgreSQL 16 and Redis 7 from the worktree compose project; the app ran on
  `http://127.0.0.1:18080` with directory provisioning enabled and an organization allowlist
  containing only the temporary fixture organization.
- The repeatable fixture passed `ServiceProviderConfig`, create, list, externalId filter,
  idempotent replay, replace with current ETag, PATCH, and `active=false` deactivation.
- Negative checks passed: invalid bearer `401`, stale `If-Match` `412`, oversized field `400`,
  oversized body `413`. Suspending the connection rejected the bearer request with `401`; the
  connection was then reactivated for the remaining checks and finally disabled.
- Persistence check: the fixture organization had two `DIRECTORY` memberships and both retained
  `user_id IS NULL`; no Namespace membership was created for those rows. SCIM audit rows used a
  null `actor_user_id` plus JSON detail identifying the SCIM connection, so the audit foreign key
  remained valid without creating a synthetic platform account.
- Cleanup: the app process was stopped, the worktree compose containers and network were removed,
  and the temporary Directory Connection was disabled before shutdown. The compose PostgreSQL
  volume remains stopped and isolated so it can be removed explicitly if the reviewer wants to
  discard the local fixture data.
- Full backend regression after the fix: `make test-backend-app` succeeded with 2050 tests,
  0 failures, 0 errors, and 28 skipped. Existing H2/collector/network warnings were emitted by
  unrelated tests; they did not fail the build.
