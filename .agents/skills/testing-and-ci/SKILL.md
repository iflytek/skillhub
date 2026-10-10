---
name: testing-and-ci
description: Testing conventions, CI pipeline rules, and smoke test coverage for SkillHub. Ensures agents write tests correctly and understand the CI gate requirements.
license: Apache-2.0
---

# Testing and CI Skill

## Trigger

Use this skill when:
- Adding or modifying backend tests
- Adding or modifying frontend tests
- Changing CI/CD workflows
- Adding smoke tests or E2E tests

## Rules

### Backend Testing

Tests live alongside source in each module's `src/test/java/`:
- `server/skillhub-app/src/test/java/` — Controller integration tests, service tests
- `server/skillhub-domain/src/test/java/` — Domain service unit tests
- `server/skillhub-auth/src/test/java/` — Auth flow tests

**Tools**: JUnit 5 + Mockito + AssertJ + Spring Boot test slices (`@WebMvcTest`, `@DataJpaTest`)

**Build commands:**
```bash
make test-backend-app   # skillhub-app + dependencies (includes -am)
make test-backend       # all backend modules
```

**Never** run `./mvnw -pl skillhub-app clean test` directly under `server/`.
`skillhub-app` depends on sibling modules, and a standalone clean build can fall back to stale
artifacts from the local Maven repository, surfacing misleading `cannot find symbol` and
signature-mismatch errors. Use `-am`, or the Makefile targets above.

**Test naming conventions:**
- Controller tests: `{ControllerName}Test.java` (e.g., `SkillControllerTest.java`)
- Service tests: `{ServiceName}Test.java`
- Integration tests: `{FlowName}IntegrationTest.java`
- Security tests: `{ControllerName}SecurityTest.java`

### Frontend Testing

**Tools**: Vitest (unit), Playwright (E2E)

```bash
make test-frontend            # Vitest unit tests (pnpm run test)
make test-e2e-frontend        # Playwright E2E tests
make test-e2e-smoke-frontend  # Playwright smoke tests (subset)
```

E2E tests live in `web/e2e/`.

### Smoke Tests

Smoke tests validate end-to-end operator workflows against a running backend:

| Script | Purpose |
|--------|---------|
| `scripts/smoke-test.sh` | Basic API health, auth, label CRUD |
| `scripts/namespace-smoke-test.sh` | Namespace creation, membership, publishing |
| `scripts/governance-smoke-test.sh` | Governance and moderation flows |
| `scripts/promotion-smoke-test.sh` | Skill promotion between scopes |

When operator-facing workflows change, update the corresponding smoke test.

### CI Pipeline

GitHub Actions workflows in `.github/workflows/`:

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `pr-tests.yml` | PR | Backend + frontend unit tests |
| `pr-e2e.yml` | PR | E2E smoke tests against staging |
| `pr-batch-test-deploy.yml` | workflow_dispatch | Batch test and deploy |
| `publish-images.yml` | release published / workflow_dispatch | Build and publish Docker images to GHCR |
| `deploy-docs.yml` | push to docs | Deploy documentation site |
| `issue-triage.yml` | issues | Auto-triage incoming issues |
| `issue-backlog-rescore.yml` | cron (every 6h) | Rescore backlog issues |
| `release-notes.yml` | workflow_dispatch | Generate release notes |
| `deepwiki.yml` | release published | Update DeepWiki documentation |
| `claim-issue-reward.yml` | issue_comment | Auto-claim issue rewards |
| `statistic-member-reward.yml` | cron/schedule | Calculate member rewards |

All workflows live in `.github/workflows/`. Deno scripts for triage, release notes, and rewards
live in `.github/scripts/`.

### Staging

Before opening a PR, validate with staging:

```bash
make staging          # Build backend Docker image + frontend static + smoke test
make staging-down     # Tear down
SERVICE=web make staging-logs  # View Nginx logs
```

Staging validates the containerized deployment path:
- Backend: built as Docker image from local source (`Dockerfile.dev`)
- Frontend: built as static files (`pnpm build`), served by Nginx
- Dependencies: same Postgres/Redis/MinIO as local dev

If staging passes, the environment stays running at:
- Web UI: `http://localhost`
- Backend API: `http://localhost:8080`

### Pre-PR Testing Checklist

- [ ] `make test-backend-app` passes
- [ ] `make typecheck-web` passes
- [ ] `make lint-web` passes (if frontend changed)
- [ ] `make staging` passes (full regression)
- [ ] If API changed: `make generate-api` run and generated file committed
- [ ] New behavior has corresponding tests
