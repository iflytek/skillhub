# Development Workflow

This document describes the recommended workflow for developing SkillHub locally.

## Prerequisites

- Docker Desktop (for dependency services and staging)
- Java 17 (for running the backend locally)
- Node.js 22 + pnpm (for running the frontend locally)
- `gh` CLI (for creating pull requests): https://cli.github.com/

## Stage 1: Local Development (fast iteration)

Use this stage for active development — writing code, fixing bugs, iterating quickly.

### Start the standard local source stack

```bash
scripts/dev/local-mysql-local-index-memory-up.sh
```

This starts the repository-owned local source combination:

- dependency service: MySQL via Docker
- backend: Spring Boot jar on your machine at `http://localhost:8080`
- frontend: Vite on your machine at `http://localhost:3000`
- mock third-party UASS page: Vite on `http://localhost:3001`
- runtime state: `memory`
- search provider: `local-file-index`

For the exact environment variables and fallback combinations such as `mysql-like`, see:

- [local-runtime-quickstart.md](./local-runtime-quickstart.md)
- [../design/runtime/runtime-core-configuration-reference.md](../design/runtime/runtime-core-configuration-reference.md)

### Backend restarts

**Frontend:** Vite HMR is enabled by default. Save a file and the browser updates instantly.

**Backend:** the local server now runs from a packaged Spring Boot jar instead of `spring-boot:run`. This avoids mixed classpaths across `skillhub-app`, `skillhub-auth`, `skillhub-domain`, and other sibling modules.

After editing backend code, restart the backend explicitly:

```bash
pkill -f 'skillhub-app-0.1.0.jar' || true
mvn -q -f server/pom.xml -pl skillhub-app -am package -DskipTests
env \
  SKILLHUB_SEARCH_PROVIDER=local-file-index \
  SKILLHUB_RUNTIME_STATE_PROVIDER=memory \
  SPRING_PROFILES_ACTIVE=local-mysql \
  SKILLHUB_AUTH_UASS_ENABLED=true \
  SKILLHUB_AUTH_UASS_MOCK_LOGIN_BASE_URL=http://localhost:3001 \
  java -jar server/skillhub-app/target/skillhub-app-0.1.0.jar
```

If you want to stop the whole local source stack, use:

```bash
scripts/dev/local-mysql-local-index-memory-down.sh
```

### Mock authentication

Two mock users are available in local mode (no password needed):

| User ID       | Role        | Header                           |
|---------------|-------------|----------------------------------|
| `local-user`  | Regular user | `X-Mock-User-Id: local-user`   |
| `local-admin` | Super admin  | `X-Mock-User-Id: local-admin`  |

Local development also creates a password-based bootstrap admin by default.
Use `BOOTSTRAP_ADMIN_USERNAME` / `BOOTSTRAP_ADMIN_PASSWORD` to log in through
the normal local account form. The default local fallback credentials are
`admin` / `ChangeMe!2026`.
To disable it for local source startup, set the environment variable
`BOOTSTRAP_ADMIN_ENABLED=false` before starting the backend.
For container or release environments, set the same value in `.env.release`
or the Compose environment.

### Local UASS configuration

If you are integrating the enterprise UASS login flow locally, the current source startup path already supports the local mock browser flow:

- login page on `3000`
- mock third-party page on `3001`
- callback back into the main app on `3000`

For current bootstrap admin semantics:

- `uass-admin-003` is the configured bootstrap admin `ussId`
- listed bootstrap admin users are granted `SUPER_ADMIN` on first account creation

### Useful commands

| Command                          | Description                      |
|----------------------------------|----------------------------------|
| `scripts/dev/local-mysql-local-index-memory-up.sh` | Start the standard local source stack |
| `scripts/dev/local-mysql-local-index-memory-down.sh` | Stop the standard local source stack |
| `scripts/dev/local-mysql-local-index-memory-status.sh` | Check ports, docker status, and health |
| `docker compose up -d mysql` | Start MySQL only |
| `docker compose stop mysql` | Stop MySQL only |

### Claude + Codex parallel workflow

When two agents need to work in parallel, do not point both of them at the same checkout. Create isolated task worktrees instead:

```bash
make parallel-init TASK=legal-pages
```

That creates dedicated Claude, Codex, and integration worktrees as sibling directories. Keep `localhost:3000` reserved for the integration worktree only.

After the one-time setup, switch to the integration worktree for the daily merge + verification loop:

```bash
cd ../skillhub-integration-legal-pages
make parallel-up
```

Then verify the merged result at http://localhost:3000.

Because all worktrees share the same local dependency project, you only need one set of dependency containers for all of them.

If you need to inspect or resolve merge conflicts before starting the app, you can still split the flow manually:

```bash
cd ../skillhub-integration-legal-pages
make parallel-sync
scripts/dev/local-mysql-local-index-memory-up.sh
```

Additional rules:

- Never let two agents write to the same checkout at the same time.
- Reserve browser verification on `localhost:3000` for the integration worktree only.
- Run `make parallel-sync` before final verification whenever agent branches have diverged.

## Stage 2: Staging Regression (pre-PR validation)

Use this stage when a feature or bugfix is complete and you want to verify it works correctly in a Docker environment before pushing.

### What staging does

The staging flow still targets a **hybrid** Docker environment:
- **Backend**: built as a Docker image from your local source
- **Frontend**: built as static files (`pnpm build`) and served by Nginx
- **Dependencies**: same MySQL/Redis/MinIO as local dev

This is faster than building both images but still validates the containerized backend and the production Nginx serving path.

### Run staging

Older project materials may still mention `make staging`, but this checkout does not currently carry the historical top-level `Makefile`.

If you need staging validation from this checkout, use the compose and build files directly or restore the repository-owned orchestration entrypoint before relying on the older command names.

This will:
1. Build the backend Docker image
2. Build the frontend static files
3. Start all services
4. Run smoke tests against the API
5. Print pass/fail summary

If all tests pass, the environment stays running at:
- Web UI: http://localhost
- Backend API: http://localhost:8080

### Stop staging

If you are still using the historical orchestration entrypoint in another checkout, stop it there.
For this checkout, staging orchestration should be considered an engineering follow-up item rather than a ready-to-run repository command.

### View staging logs

For this checkout, inspect the relevant compose services directly if you run staging by hand.

## Stage 3: Create Pull Request

After staging passes, push and create the PR with your normal git + `gh` workflow.

This will:
1. Check for uncommitted changes (prompts to commit if any)
2. Push your branch to origin
3. Create a pull request using `gh pr create --fill`

The PR title and body are auto-populated from your commit messages.

> **Note:** the historical `make pr` command name may still appear in older notes, but PR creation in this checkout should be treated as normal `git push` + `gh pr create` workflow instead of a guaranteed repository script.

## Full workflow summary

```bash
scripts/dev/local-mysql-local-index-memory-up.sh
# ... write code, test in browser ...
# run manual staging validation if needed
# push and open PR with git + gh
```
