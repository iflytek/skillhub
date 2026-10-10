---
name: dev-workflow
description: The complete development workflow for SkillHub contributors including local dev, staging validation, testing, and PR creation. Ensures agents follow the correct sequence of steps.
license: Apache-2.0
---

# Development Workflow Skill

## Trigger

Use this skill when:
- Starting local development
- Running tests or validation
- Preparing a pull request
- Setting up the development environment
- Working with parallel agent worktrees

## Prerequisites

- Java 21+ (`java -version`)
- Maven wrapper (`./mvnw` in `server/`)
- Node.js + pnpm
- Docker + docker compose
- `gh` CLI (for PR creation)
- `curl` (for smoke tests and health checks)

## Workflow Stages

### Stage 1: Local Development (fast iteration)

**One-command start:**

```bash
make dev-all          # Start full stack: Postgres, Redis, MinIO, scanner, backend, frontend
make dev-all-down     # Stop everything
make dev-all-reset    # Full reset (clears data volumes)
make dev-status       # Check service status
```

**Access points:**
- Web UI: `http://localhost:3000`
- Backend API: `http://localhost:8080`
- Scanner: `http://localhost:8000`

**Individual components:**

```bash
make dev              # Start dependency services only (Postgres, Redis, MinIO, scanner)
make dev-server       # Start backend in foreground (blocking)
make dev-web          # Start Vite dev server (HMR enabled)
make dev-server-restart # Restart backend process
make dev-down         # Stop dependency services
make dev-logs         # View backend logs (use SERVICE=frontend for frontend logs)
```

**Backend development**: After editing Java code, run `make dev-server-restart`.

**Frontend development**: Vite HMR enabled — save a file for instant browser updates.

**Scanner**: The security scanner is enabled by default in dev. Health checked at `http://localhost:8000/health`.

### Stage 2: Testing

| Command | Scope | Notes |
|---------|-------|-------|
| `make test-backend-app` | Backend unit tests | skillhub-app + dependencies (`-am`) |
| `make test-backend` | All backend modules | All modules via `./mvnw test` |
| `make test-frontend` | Frontend unit tests | Vitest (pnpm run test) |
| `make test-e2e-frontend` | Frontend E2E tests | Playwright |
| `make test-e2e-smoke-frontend` | Frontend E2E smoke | Playwright subset |
| `make typecheck-web` | TypeScript type check | `tsc --noEmit` |
| `make lint-web` | ESLint check | Frontend linting |

**Important**: Never run `./mvnw -pl skillhub-app clean test` directly under `server/`.
Use `-am` or Makefile targets to include dependent modules.

### Stage 3: Staging Regression (pre-PR validation)

```bash
make staging          # Build backend Docker image + frontend static + smoke test
make staging-down     # Tear down
make staging-logs     # View backend logs
SERVICE=web make staging-logs  # View Nginx logs
```

Staging validates the containerized deployment path:
- Backend: built as Docker image from local source (`Dockerfile.dev`)
- Frontend: built as static files (`pnpm build`), served by Nginx
- Dependencies: same Postgres/Redis/MinIO as local dev
- Smoke test runs against staging via `scripts/smoke-test.sh`

**Staging URLs:**
- Web UI: `http://localhost`
- Backend API: `http://localhost:8080`

**Staging credentials** (for bootstrap admin):
- Username: `admin`
- Password: `Admin@staging2026`

### Stage 4: Pull Request

```bash
make pr               # Push branch + create PR (requires gh CLI)
```

Requirements:
- `gh` CLI installed and authenticated
- Not on main/master branch
- All changes committed (will prompt if uncommitted changes exist)

### Useful Commands

| Command | Description |
|---------|-------------|
| `make generate-api` | Regenerate OpenAPI types from running backend |
| `make namespace-smoke` | Namespace workflow smoke test |
| `make db-reset` | Reset database only (Flyway migrate) |
| `make validate-release-config` | Validate release env vars (.env.release) |
| `./scripts/smoke-test.sh` | Basic API smoke test (health, auth, labels) |
| `./scripts/namespace-smoke-test.sh` | Namespace CRUD + membership smoke test |
| `./scripts/check-openapi-generated.sh` | Verify OpenAPI types are not stale |
| `make parallel-init TASK=name` | Create parallel worktree for agent |

### Mock Auth Users

| User ID | Role | Header |
|---------|------|--------|
| `local-user` | Regular user | `X-Mock-User-Id: local-user` |
| `local-admin` | Super admin | `X-Mock-User-Id: local-admin` |

Bootstrap admin (local profile):
- Username: `admin`
- Password: `ChangeMe!2026`

### Smoke Test Coverage

`scripts/smoke-test.sh` validates:
1. Health endpoint (`/actuator/health` → 200)
2. Prometheus metrics (`/actuator/prometheus` → 200)
3. Namespaces API (`/api/v1/namespaces` → 200)
4. Auth required (`/api/v1/auth/me` → 401 without session)
5. User registration flow (with CSRF)
6. Auth me with session
7. Password change
8. Logout + verify 401 after
9. Admin login
10. Label CRUD (admin only)

Additional smoke tests:
- `scripts/namespace-smoke-test.sh` — Namespace creation, membership, publishing
- `scripts/governance-smoke-test.sh` — Governance and moderation
- `scripts/promotion-smoke-test.sh` — Skill promotion between scopes

### Parallel Agent Workflow

For parallel agent development with isolated worktrees:

```bash
make parallel-init TASK=feature-name           # Create worktree
make parallel-sync SOURCES="feat1 feat2"       # Merge feature branches
make parallel-up SOURCES="feat1 feat2"         # Merge + start dev environment
make parallel-down                             # Stop parallel environment
```

See `docs/13-parallel-workflow.md` for full details.

### Commit Style

Use conventional commit format:

```
<type>(<scope>): <description>
```

Examples:
```
feat(auth): add local account login
fix(publish): resolve null pointer when skill metadata is missing name
docs(deploy): clarify runtime image usage
test(namespace): add membership service edge case tests
refactor(review): extract query repository for governance list
chore(ci): add parallel workflow scripts
```

### Common Issues

| Issue | Solution |
|-------|----------|
| Backend won't start | Check Java version (`java -version`), must be 21+ |
| Port 8080 in use | `lsof -i :8080` to find and kill the process |
| Maven download timeout | Configure mirror in `~/.m2/settings.xml` |
| Frontend won't start | Run `make web-deps` to ensure node_modules exist |
| Staging build fails | Check `Dockerfile.dev` and ensure Maven build succeeds first |
| CSRF errors in tests | Ensure cookie jar is shared and CSRF token refreshed after login |
