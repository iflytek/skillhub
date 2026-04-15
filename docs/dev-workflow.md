# Development Workflow

This document describes the recommended workflow for developing SkillHub locally.

## Prerequisites

- Docker Desktop (for running the local stack in containers)
- Java 21 (optional — only if you run the backend outside Docker)
- Node.js 22 + pnpm (optional — only if you run the frontend outside Docker)
- `gh` CLI (for creating pull requests): https://cli.github.com/

## Stage 1: Local development (fast iteration)

Use this stage for active development — writing code, fixing bugs, iterating quickly.

### Start the full local stack

```bash
docker compose -f compose.dev.yml up -d --build
```

This starts:

- Dependency services (Postgres, Redis, MinIO) in Docker
- Skill scanner in Docker
- Backend (Spring Boot) in Docker at `http://localhost:8080`
- Frontend (Vite) in Docker at `http://localhost:3000`

### Logs

```bash
docker compose -f compose.dev.yml logs -f server
docker compose -f compose.dev.yml logs -f web
```

### Rebuild after code changes

`compose.dev.yml` builds images from local source (`build.context`). After you change Java or frontend code, rebuild the affected service:

```bash
docker compose -f compose.dev.yml up -d --build server
docker compose -f compose.dev.yml up -d --build web
```

### Stop / reset

Stop containers:

```bash
docker compose -f compose.dev.yml down
```

Wipe local volumes (database + MinIO data, etc.):

```bash
docker compose -f compose.dev.yml down -v
```

### Dependency-only mode (optional)

If you only want Postgres/Redis/MinIO/scanner without the app containers, use:

```bash
docker compose -f docker-compose.yml up -d --build
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

### Claude + Codex parallel workflow

When two agents need to work in parallel, do not point both of them at the same checkout. Create isolated task worktrees instead:

```bash
./scripts/parallel-init.sh legal-pages
```

That creates dedicated Claude, Codex, and integration worktrees as sibling directories. Keep `localhost:3000` reserved for the integration worktree only.

After the one-time setup, switch to the integration worktree for the daily merge + verification loop:

```bash
cd ../skillhub-integration-legal-pages
./scripts/parallel-up.sh
```

Then verify the merged result at http://localhost:3000.

If you need to inspect or resolve merge conflicts before starting the app, you can still split the flow manually:

```bash
cd ../skillhub-integration-legal-pages
./scripts/parallel-sync.sh
docker compose -f compose.dev.yml up -d --build
```

See [13-parallel-workflow.md](./13-parallel-workflow.md) for the full workflow, responsibilities, merge rules, and recovery guidance.

## Stage 2: Production Compose smoke (optional)

Use this when you want to validate **prebuilt images** (Nginx static UI + containerized API) before pushing.

1. Build and tag images locally (or pull from GHCR):

```bash
docker build -t skillhub-server:local -f server/Dockerfile server
docker build -t skillhub-web:local -f web/Dockerfile web
```

2. Copy `.env.example` to `.env` and set:

- `SKILLHUB_SERVER_IMAGE=skillhub-server:local`
- `SKILLHUB_WEB_IMAGE=skillhub-web:local`
- a strong `POSTGRES_PASSWORD`

3. Start:

```bash
docker compose --env-file .env -f compose.prod.yml up -d --wait
```

Stop:

```bash
docker compose --env-file .env -f compose.prod.yml down
```

## Stage 3: Create Pull Request

After your checks pass:

```bash
make pr
```

This will:
1. Check for uncommitted changes (prompts to commit if any)
2. Push your branch to origin
3. Create a pull request using `gh pr create --fill`

The PR title and body are auto-populated from your commit messages.

> **Note:** `make pr` requires an interactive terminal. Do not use it in CI.

## Full workflow summary

```
docker compose -f compose.dev.yml up -d --build   # start local dev
# ... write code, test in browser ...
docker compose --env-file .env -f compose.prod.yml up -d --wait   # optional image smoke
docker compose --env-file .env -f compose.prod.yml down           # optional stop
make pr                                           # push + create PR
```
