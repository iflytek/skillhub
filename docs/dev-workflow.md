# Development Workflow

This document describes the recommended workflow for developing SkillHub locally.

## Prerequisites

- Docker Desktop (for dependency services and staging)
- Java 21 (for running the backend locally)
- Node.js 22 + pnpm (for running the frontend locally)
- `gh` CLI (for creating pull requests): https://cli.github.com/

## Stage 1: Local Development (fast iteration)

Use this stage for active development — writing code, fixing bugs, iterating quickly.

### Start the full local stack

```bash
make dev-all
```

This starts:
- Dependency services (Postgres, Redis, MinIO) via Docker
- Backend (Spring Boot) directly on your machine at http://localhost:8080
- Frontend (Vite) directly on your machine at http://localhost:3000

### Hot reload

**Frontend:** Vite HMR is enabled by default. Save a file and the browser updates instantly.

**Backend:** Spring Boot DevTools is configured. After editing Java code:
1. In IntelliJ IDEA: press `Cmd+F9` (Build Project)
2. The backend restarts automatically in 3-8 seconds
3. Watch the terminal running `make dev-server` for the restart log

### Mock authentication

Two mock users are available in local mode (no password needed):

| User ID       | Role        | Header                           |
|---------------|-------------|----------------------------------|
| `local-user`  | Regular user | `X-Mock-User-Id: local-user`   |
| `local-admin` | Super admin  | `X-Mock-User-Id: local-admin`  |

### Useful commands

| Command                          | Description                      |
|----------------------------------|----------------------------------|
| `make dev-all`                   | Start full local stack           |
| `make dev-all-down`              | Stop all local services          |
| `make dev-status`                | Check status of all services     |
| `make dev-logs`                  | Tail backend logs                |
| `SERVICE=frontend make dev-logs` | Tail frontend logs               |
| `make dev-all-reset`             | Full reset (clears data volumes) |
| `make db-reset`                  | Reset database only              |

## Stage 2: Staging Regression (pre-PR validation)

Use this stage when a feature or bugfix is complete and you want to verify it works correctly in a Docker environment before pushing.

### What staging does

`make staging` runs a **hybrid** Docker environment:
- **Backend**: built as a Docker image from your local source
- **Frontend**: built as static files (`pnpm build`) and served by Nginx
- **Dependencies**: same Postgres/Redis/MinIO as local dev

This is faster than building both images but still validates the containerized backend and the production Nginx serving path.

### Run staging

```bash
make staging
```

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

```bash
make staging-down
```

### View staging logs

```bash
make staging-logs            # backend logs
SERVICE=web make staging-logs  # nginx logs
```

## Stage 3: Create Pull Request

After staging passes:

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
make dev-all          # start local dev
# ... write code, test in browser ...
make staging          # regression test in Docker
make staging-down     # stop staging
make pr               # push + create PR
```
