# Dev Workflow Optimization Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Optimize the development workflow with fast-restart backend (DevTools), a hybrid Docker staging environment, and automated PR creation.

**Architecture:** Two-stage workflow — local dev with Spring Boot DevTools for fast restarts + Vite HMR, then a hybrid staging mode that builds only the backend Docker image while mounting locally-built frontend static files into Nginx, followed by automated PR creation via `make pr`.

**Tech Stack:** Spring Boot DevTools, Docker Compose (staging overlay), Nginx (static file mount), GNU Make, gh CLI, bash

---

## Chunk 1: Spring Boot DevTools

### Task 1: Add Spring Boot DevTools to backend

**Files:**
- Modify: `server/skillhub-app/pom.xml`

- [ ] **Step 1: Add DevTools dependency**

In `server/skillhub-app/pom.xml`, add inside `<dependencies>` before the test dependencies:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <scope>runtime</scope>
    <optional>true</optional>
</dependency>
```

- [ ] **Step 2: Verify DevTools is excluded from production build**

DevTools is automatically excluded when running as a fully packaged JAR (the `optional` flag ensures it's not transitively included). The existing `spring-boot-maven-plugin` configuration in `server/skillhub-app/pom.xml` has no `<configuration>` block, so the default behavior (exclude DevTools from production) applies. No changes needed.

- [ ] **Step 3: Verify local profile still works**

First start the dependency services, then start the backend:

```bash
make dev
```

Expected: Docker services (Postgres, Redis, MinIO) start and become healthy.

Then in the same or a new terminal:

```bash
make dev-server
```

Expected: Backend starts, logs show `LiveReload server is running on port 35729` — this confirms DevTools is active.

- [ ] **Step 4: Test fast restart**

With backend running via `make dev-server`:
1. Edit any Java file (e.g., add a comment to a controller)
2. In IntelliJ: press `Cmd+F9` (Build Project)
3. Watch the terminal running `dev-server`

Expected: Application restarts in 3-8 seconds (vs 30-60 seconds for a cold start). Log line: `Restarting due to classpath changes`.

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-app/pom.xml
git commit -m "feat(dev): add Spring Boot DevTools for fast restart in local dev"
```

---

## Chunk 2: Makefile dev-logs and dev-status targets

### Task 2: Add dev-logs and dev-status Makefile targets

**Files:**
- Modify: `Makefile` (will update `.PHONY` line in this chunk and again in Chunks 4 and 5 — each chunk appends new targets to the same line)

- [ ] **Step 1: Add targets to .PHONY line**

In `Makefile` line 1, add `dev-logs dev-status` to the `.PHONY` list after the existing targets.

- [ ] **Step 2: Add dev-status target**

After the `dev-all-reset` target (around line 106), add the following. Note: these targets reference variables (`DEV_PROCESS`, `DEV_SERVER_PID`, `DEV_WEB_PID`, `DEV_SERVER_LOG`, `DEV_WEB_LOG`) that are already defined at the top of the Makefile (lines 3-10).

```makefile
dev-status: ## 查看本地开发服务状态
	@echo "=== Dependency Services ==="
	@docker compose ps
	@echo ""
	@echo "=== Backend ==="
	@if $(DEV_PROCESS) status --pid-file $(DEV_SERVER_PID) >/dev/null 2>&1; then \
		echo "  Running (PID $$(cat $(DEV_SERVER_PID)))"; \
	else \
		echo "  Not running"; \
	fi
	@echo "=== Frontend ==="
	@if $(DEV_PROCESS) status --pid-file $(DEV_WEB_PID) >/dev/null 2>&1; then \
		echo "  Running (PID $$(cat $(DEV_WEB_PID)))"; \
	else \
		echo "  Not running"; \
	fi

dev-logs: ## 实时查看开发服务日志（backend/frontend，默认 backend）
	@SERVICE=$${SERVICE:-backend}; \
	if [ "$$SERVICE" = "backend" ]; then \
		tail -f $(DEV_SERVER_LOG); \
	elif [ "$$SERVICE" = "frontend" ]; then \
		tail -f $(DEV_WEB_LOG); \
	else \
		echo "Unknown service: $$SERVICE. Use SERVICE=backend or SERVICE=frontend"; \
		exit 1; \
	fi
```

- [ ] **Step 3: Verify targets work**

```bash
make dev-status
```

Expected: Shows docker compose service status and backend/frontend process status.

```bash
# In a separate terminal while dev-all is running:
make dev-logs
# Or for frontend:
SERVICE=frontend make dev-logs
```

Expected: Tails the respective log file.

- [ ] **Step 4: Commit**

```bash
git add Makefile
git commit -m "feat(dev): add dev-status and dev-logs make targets"
```

---

## Chunk 3: Staging Docker Compose (hybrid mode)

### Task 3: Create docker-compose.staging.yml

**Files:**
- Create: `docker-compose.staging.yml`

The staging compose file uses:
- Backend: locally-built Docker image (`skillhub-server:staging`)
- Frontend: locally-built static files (`web/dist/`) mounted into an Nginx container using the existing `nginx.conf.template`
- Dependencies: same Postgres/Redis/MinIO as dev (reuses `docker-compose.yml` via `--file` flag)

**Important:** This file references `postgres`, `redis`, and `minio` services in `depends_on` clauses, but does NOT define them. When Docker Compose is invoked with `-f docker-compose.yml -f docker-compose.staging.yml`, it merges both files, so the dependency services from `docker-compose.yml` are available to the `server` and `web` services defined here.

- [ ] **Step 1: Create docker-compose.staging.yml**

```yaml
# Staging environment: hybrid mode
# - Backend: locally built Docker image
# - Frontend: locally built static files mounted into Nginx
# - Dependencies: reuses docker-compose.yml (postgres, redis, minio)
#
# Usage: make staging
# Do NOT use directly — use the make target which handles build + deps + cleanup.

services:
  server:
    image: skillhub-server:staging
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/skillhub
      SPRING_DATASOURCE_USERNAME: skillhub
      SPRING_DATASOURCE_PASSWORD: skillhub_dev
      REDIS_HOST: redis
      REDIS_PORT: 6379
      SESSION_COOKIE_SECURE: "false"
      SKILLHUB_PUBLIC_BASE_URL: "http://localhost"
      DEVICE_AUTH_VERIFICATION_URI: "http://localhost/api/device/activate"
      SKILLHUB_STORAGE_PROVIDER: s3
      STORAGE_BASE_PATH: /var/lib/skillhub/storage
      SKILLHUB_STORAGE_S3_ENDPOINT: http://minio:9000
      SKILLHUB_STORAGE_S3_PUBLIC_ENDPOINT: http://localhost:9000
      SKILLHUB_STORAGE_S3_BUCKET: skillhub
      SKILLHUB_STORAGE_S3_ACCESS_KEY: minioadmin
      SKILLHUB_STORAGE_S3_SECRET_KEY: minioadmin
      SKILLHUB_STORAGE_S3_REGION: us-east-1
      SKILLHUB_STORAGE_S3_FORCE_PATH_STYLE: "true"
      SKILLHUB_STORAGE_S3_AUTO_CREATE_BUCKET: "true"
      BOOTSTRAP_ADMIN_ENABLED: "true"
      BOOTSTRAP_ADMIN_USER_ID: staging-admin
      BOOTSTRAP_ADMIN_USERNAME: admin
      BOOTSTRAP_ADMIN_PASSWORD: "Admin@staging2026"
      BOOTSTRAP_ADMIN_DISPLAY_NAME: Admin
      BOOTSTRAP_ADMIN_EMAIL: admin@skillhub.local
      OAUTH2_GITHUB_CLIENT_ID: local-placeholder
      OAUTH2_GITHUB_CLIENT_SECRET: local-placeholder
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8080/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 60s

  web:
    image: nginx:alpine
    ports:
      - "80:80"
    volumes:
      - ./web/dist:/usr/share/nginx/html:ro
      - ./web/nginx.conf.template:/etc/nginx/templates/default.conf.template:ro
    environment:
      SKILLHUB_API_UPSTREAM: http://server:8080
      SKILLHUB_WEB_API_BASE_URL: ""
      SKILLHUB_PUBLIC_BASE_URL: ""
    depends_on:
      server:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://127.0.0.1/nginx-health"]
      interval: 10s
      timeout: 5s
      retries: 6
      start_period: 10s
```

- [ ] **Step 2: Verify nginx.conf.template is compatible with nginx:alpine**

The existing `web/nginx.conf.template` uses `${SKILLHUB_API_UPSTREAM}` variable substitution. The official `nginx:alpine` image supports this via `envsubst` when templates are placed in `/etc/nginx/templates/` — files are processed at container startup. This is already the correct path in the compose file above.

No changes needed to `nginx.conf.template`.

- [ ] **Step 3: Commit**

```bash
git add docker-compose.staging.yml
git commit -m "feat(staging): add hybrid staging docker-compose"
```

---

## Chunk 4: Staging Makefile targets

### Task 4: Add staging targets to Makefile

**Files:**
- Modify: `Makefile` (will update `.PHONY` line again — appending to the changes from Chunk 2)

The `make staging` command will:
1. Build backend Docker image from source
2. Build frontend static files with `pnpm build`
3. Start dependency services (reuse dev docker-compose)
4. Start staging services (backend container + nginx with mounted dist)
5. Wait for health checks
6. Run smoke tests against `http://localhost:8080`
7. Print pass/fail summary
8. On failure: print logs and exit non-zero

**Note on Docker Compose project name:** Both `docker compose` invocations (step 3 and step 4) run from the same directory and use the same project name (default: directory name `skillhub`), so they share the same network and the `server` container can reach `postgres`/`redis`/`minio` by hostname.

- [ ] **Step 1: Add staging variables at top of Makefile**

After the existing `DEV_API_URL` line (line 9), add:

```makefile
STAGING_API_URL := http://localhost:8080
STAGING_WEB_URL := http://localhost
STAGING_SERVER_IMAGE := skillhub-server:staging
```

- [ ] **Step 1.5: Verify prerequisites**

Verify that `scripts/smoke-test.sh` exists and is executable:

```bash
ls -l scripts/smoke-test.sh
```

Expected: File exists and has execute permissions (`-rwxr-xr-x` or similar).

Also verify the backend Docker build context structure:

```bash
ls server/
```

Expected output should include: `pom.xml`, `mvnw`, `.mvn/`, `skillhub-app/`, `skillhub-domain/`, `skillhub-auth/`, `skillhub-search/`, `skillhub-infra/`, `skillhub-storage/`, and `Dockerfile`. These are all required by `server/Dockerfile`.

- [ ] **Step 2: Add staging targets to .PHONY line**

Add `staging staging-down staging-logs` to the `.PHONY` list on line 1 (appending to the changes from Chunk 2).

- [ ] **Step 3: Add staging targets after the dev-all-reset block**

After `dev-all-reset` target, add:

```makefile
staging: ## 构建并启动 staging 环境，运行 smoke test（混合模式：后端镜像 + 前端静态文件）
	@echo "=== [1/5] Building backend Docker image ==="
	docker build -t $(STAGING_SERVER_IMAGE) -f server/Dockerfile server
	@echo "=== [2/5] Building frontend static files ==="
	cd web && pnpm run build
	@echo "=== [3/5] Starting dependency services ==="
	docker compose up -d --wait
	@echo "=== [4/5] Starting staging services ==="
	docker compose -f docker-compose.yml -f docker-compose.staging.yml up -d --wait server web
	@echo "=== [5/5] Running smoke tests ==="
	@if bash scripts/smoke-test.sh $(STAGING_API_URL); then \
		echo ""; \
		echo "Staging passed. Environment is running:"; \
		echo "  Web UI:  $(STAGING_WEB_URL)"; \
		echo "  Backend: $(STAGING_API_URL)"; \
		echo ""; \
		echo "Run 'make staging-down' to stop."; \
		echo "Run 'make pr' to create a pull request."; \
	else \
		echo ""; \
		echo "Smoke tests FAILED. Printing logs..."; \
		docker compose -f docker-compose.yml -f docker-compose.staging.yml logs server; \
		$(MAKE) staging-down; \
		exit 1; \
	fi

staging-down: ## 停止 staging 环境
	docker compose -f docker-compose.yml -f docker-compose.staging.yml down --remove-orphans

staging-logs: ## 查看 staging 服务日志（SERVICE=server|web，默认 server）
	@SERVICE=$${SERVICE:-server}; \
	docker compose -f docker-compose.yml -f docker-compose.staging.yml logs -f $$SERVICE
```

- [ ] **Step 4: Verify staging target syntax**

```bash
make help
```

Expected: `staging`, `staging-down`, `staging-logs` appear in the help output with their descriptions.

- [ ] **Step 5: Commit**

```bash
git add Makefile
git commit -m "feat(staging): add staging make targets for hybrid docker regression testing"
```

---

## Chunk 5: make pr target

### Task 5: Add make pr target to Makefile

**Files:**
- Modify: `Makefile` (will update `.PHONY` line again — appending to the changes from Chunks 2 and 4)

The `make pr` command will:
1. Check that `gh` CLI is installed and authenticated
2. Check for uncommitted changes and prompt to commit or abort
3. Push current branch to origin
4. Create a PR using `gh pr create` with auto-generated title from branch name and body template

**Note:** This target uses `read -r` for interactive prompts, so it requires an interactive terminal. It will not work in CI or non-interactive contexts.

- [ ] **Step 1: Add pr to .PHONY line**

Add `pr` to the `.PHONY` list on line 1 (appending to the changes from Chunks 2 and 4).

- [ ] **Step 2: Add pr target after staging-logs**

```makefile
pr: ## 推送当前分支并创建 Pull Request（需要 gh CLI）
	@if ! command -v gh >/dev/null 2>&1; then \
		echo "Error: gh CLI not found. Install from https://cli.github.com/"; \
		exit 1; \
	fi
	@if ! gh auth status >/dev/null 2>&1; then \
		echo "Error: gh CLI not authenticated. Run: gh auth login"; \
		exit 1; \
	fi
	@BRANCH=$$(git rev-parse --abbrev-ref HEAD); \
	if [ "$$BRANCH" = "main" ] || [ "$$BRANCH" = "master" ]; then \
		echo "Error: Cannot create PR from main/master branch."; \
		exit 1; \
	fi
	@if ! git diff --quiet || ! git diff --cached --quiet; then \
		echo "You have uncommitted changes:"; \
		git status --short; \
		echo ""; \
		printf "Commit all changes before creating PR? [y/N] "; \
		read -r answer; \
		if [ "$$answer" = "y" ] || [ "$$answer" = "Y" ]; then \
			git add -A; \
			git commit -m "chore: pre-PR commit"; \
		else \
			echo "Aborted. Commit or stash your changes first."; \
			exit 1; \
		fi; \
	fi
	@BRANCH=$$(git rev-parse --abbrev-ref HEAD); \
	echo "Pushing branch $$BRANCH to origin..."; \
	git push -u origin "$$BRANCH"
	@echo "Creating pull request..."
	@if gh pr view >/dev/null 2>&1; then \
		echo "A pull request already exists for this branch:"; \
		gh pr view --json url -q '.url'; \
		exit 0; \
	fi
	@gh pr create --fill --web || gh pr create --fill
```

Note: `--fill` auto-populates title and body from commits. `--web` opens the browser for final editing; if that fails (non-interactive), falls back to CLI creation.

- [ ] **Step 3: Verify gh CLI is available**

```bash
gh --version
gh auth status
```

Expected: gh version output and authenticated status. If not installed, note in docs.

- [ ] **Step 4: Commit**

```bash
git add Makefile
git commit -m "feat(dev): add make pr target for automated pull request creation"
```

---

## Chunk 6: Documentation update

### Task 6: Update CONTRIBUTING / README with new workflow

**Files:**
- Modify: `README.md` (development workflow section)
- Create: `docs/dev-workflow.md`

- [ ] **Step 1: Create docs/dev-workflow.md**

Create the file `docs/dev-workflow.md` with the following content. The inner code blocks use standard triple-backtick fences; the outer `~~~` fence here is only for plan readability:

~~~markdown
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

| User ID       | Role        | Header                          |
|---------------|-------------|----------------------------------|
| `local-user`  | Regular user | `X-Mock-User-Id: local-user`   |
| `local-admin` | Super admin  | `X-Mock-User-Id: local-admin`  |

### Useful commands

| Command              | Description                              |
|----------------------|------------------------------------------|
| `make dev-all`       | Start full local stack                   |
| `make dev-all-down`  | Stop all local services                  |
| `make dev-status`    | Check status of all services             |
| `make dev-logs`      | Tail backend logs                        |
| `SERVICE=frontend make dev-logs` | Tail frontend logs          |
| `make dev-all-reset` | Full reset (clears data volumes)         |
| `make db-reset`      | Reset database only                      |

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
make staging-logs           # backend logs
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
~~~

- [ ] **Step 2: Add workflow reference to README.md**

In `README.md`, find the `### Local Development` section (around line 67). After the `make help` line (around line 97), add:

```markdown
For the full development workflow (local dev → staging → PR), see [docs/dev-workflow.md](docs/dev-workflow.md).
```

- [ ] **Step 3: Commit**

```bash
git add docs/dev-workflow.md README.md
git commit -m "docs: add dev-workflow guide covering local dev, staging, and PR creation"
```

---

## Final verification

- [ ] **End-to-end test**

```bash
# 1. Start local dev
make dev-all
# Verify: http://localhost:3000 loads, http://localhost:8080/actuator/health returns UP

# 2. Edit a Java file, press Cmd+F9 in IntelliJ
# Verify: backend restarts in <10 seconds

# 3. Edit a React file and save
# Verify: browser updates without full reload

# 4. Stop local dev
make dev-all-down

# 5. Run staging
make staging
# Verify: smoke tests pass, http://localhost loads

# 6. Stop staging
make staging-down

# 7. Check help output
make help
# Verify: staging, staging-down, staging-logs, dev-status, dev-logs, pr all appear
```
