.PHONY: help dev dev-all dev-down dev-all-down dev-all-reset dev-logs dev-status build test check clean web-deps web-install web-install-ci dev-server dev-server-restart dev-web build-backend test-backend build-frontend test-frontend test-e2e-frontend test-e2e-smoke-frontend build-web test-web typecheck-web lint-web generate-api db-reset namespace-smoke validate-release-config staging staging-down staging-logs pr parallel-init parallel-sync parallel-up parallel-down docs-dev docs-build docs-preview

DEV_DIR := .dev
DEV_SERVER_PID := $(DEV_DIR)/server.pid
DEV_WEB_PID := $(DEV_DIR)/web.pid
DEV_SERVER_LOG := $(DEV_DIR)/server.log
DEV_WEB_LOG := $(DEV_DIR)/web.log
DEV_WEB_URL := http://localhost:3000
DEV_API_URL := http://localhost:8080
DEV_SCANNER_URL := http://localhost:8000
STAGING_API_URL := http://localhost:8080
STAGING_WEB_URL := http://localhost
STAGING_SERVER_IMAGE := skillhub-server:staging
DEV_PROCESS := bash scripts/dev-process.sh
DEV_SERVER_PREPARE := true
DEV_SERVER_CMD := ./scripts/run-dev-app.sh
DEV_SERVER_SCANNER_ENV := SKILLHUB_SECURITY_SCANNER_ENABLED=true SKILLHUB_SECURITY_SCANNER_URL=$(DEV_SCANNER_URL) SKILLHUB_SECURITY_SCANNER_MODE=upload
BACKEND_TEST_JAVA_OPTIONS ?= -XX:+EnableDynamicAgentLoading
PARALLEL_BASE_REF ?= origin/main
PARALLEL_WORKTREE_ROOT ?=
DEV_COMPOSE_PROJECT_NAME ?= skillhub
STAGING_COMPOSE_PROJECT_NAME ?= skillhub-staging
DEV_COMPOSE := docker compose -p $(DEV_COMPOSE_PROJECT_NAME)
STAGING_BASE_COMPOSE := docker compose -p $(STAGING_COMPOSE_PROJECT_NAME)
STAGING_COMPOSE := $(STAGING_BASE_COMPOSE) -f docker-compose.yml -f docker-compose.staging.yml

help: ## 显示帮助
	@grep -E '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-15s\033[0m %s\n", $$1, $$2}'

dev: ## 启动本地开发环境（依赖服务，含 skill-scanner）
	$(DEV_COMPOSE) up -d --wait --remove-orphans
	@echo "Services ready."
	@echo "Start backend with: make dev-server"
	@echo "Start frontend with: make dev-web"

dev-all: ## 一键启动本地开发环境（依赖 + scanner + 后端 + 前端）
	@mkdir -p $(DEV_DIR)
	@$(MAKE) dev
	@$(MAKE) web-deps
	@if $(DEV_PROCESS) status --pid-file $(DEV_SERVER_PID) >/dev/null 2>&1; then \
		echo "Backend already running with PID $$(cat $(DEV_SERVER_PID))"; \
	else \
		echo "Starting backend..."; \
		$(DEV_PROCESS) start --pid-file $(DEV_SERVER_PID) --log-file $(DEV_SERVER_LOG) --cwd server -- /bin/sh -lc '$(DEV_SERVER_PREPARE) && exec env $(DEV_SERVER_SCANNER_ENV) $(DEV_SERVER_CMD)' >/dev/null; \
	fi
	@if $(DEV_PROCESS) status --pid-file $(DEV_WEB_PID) >/dev/null 2>&1; then \
		echo "Frontend already running with PID $$(cat $(DEV_WEB_PID))"; \
	else \
		echo "Starting frontend..."; \
		$(DEV_PROCESS) start --pid-file $(DEV_WEB_PID) --log-file $(DEV_WEB_LOG) --cwd web -- pnpm exec vite --host 0.0.0.0 --strictPort >/dev/null; \
	fi
	@echo "Waiting for backend on $(DEV_API_URL) ..."
	@backend_ready=0; \
	for attempt in 1 2; do \
		for i in $$(seq 1 30); do \
			if curl -sf $(DEV_API_URL)/actuator/health >/dev/null; then \
				echo "Backend ready."; \
				backend_ready=1; \
				break 2; \
			fi; \
			if ! $(DEV_PROCESS) status --pid-file $(DEV_SERVER_PID) >/dev/null 2>&1; then \
				break; \
			fi; \
			sleep 2; \
		done; \
		if [ "$$attempt" -lt 2 ]; then \
			echo "Backend did not become ready on attempt $$attempt. Restarting..."; \
			$(DEV_PROCESS) stop --pid-file $(DEV_SERVER_PID); \
			sleep 2; \
			$(DEV_PROCESS) start --pid-file $(DEV_SERVER_PID) --log-file $(DEV_SERVER_LOG) --cwd server -- /bin/sh -lc '$(DEV_SERVER_PREPARE) && exec env $(DEV_SERVER_SCANNER_ENV) $(DEV_SERVER_CMD)' >/dev/null; \
		fi; \
	done; \
		if [ "$$backend_ready" -ne 1 ]; then \
			echo ""; \
			echo "Backend failed to become ready. Check $(DEV_SERVER_LOG)"; \
			echo ""; \
			echo "Common issues:"; \
			echo "  1. Maven dependency download failed (network timeout)"; \
			echo "     -> Configure mirror in ~/.m2/settings.xml"; \
			echo "     -> See: https://maven.aliyun.com/mvn/guide"; \
			echo "  2. Java version mismatch (requires Java 21+)"; \
			echo "     -> Run: java -version"; \
			echo "  3. Port 8080 already in use"; \
			echo "     -> Run: lsof -i :8080"; \
			echo ""; \
			exit 1; \
		fi
	@echo "Waiting for scanner on $(DEV_SCANNER_URL) ..."
	@scanner_ready=0; \
	for i in $$(seq 1 30); do \
		if curl -sf $(DEV_SCANNER_URL)/health >/dev/null; then \
			echo "Scanner ready."; \
			scanner_ready=1; \
			break; \
		fi; \
		sleep 2; \
	done; \
	if [ "$$scanner_ready" -ne 1 ]; then \
		echo "Scanner failed to become ready. Check docker compose logs."; \
		exit 1; \
	fi
	@echo "Waiting for frontend on $(DEV_WEB_URL) ..."
	@frontend_ready=0; \
	for i in $$(seq 1 60); do \
		if curl -sf $(DEV_WEB_URL) >/dev/null; then \
			echo "Frontend ready."; \
			frontend_ready=1; \
			break; \
		fi; \
		sleep 2; \
	done; \
	if [ "$$frontend_ready" -ne 1 ]; then \
		echo "Frontend failed to become ready. Check $(DEV_WEB_LOG)"; \
		exit 1; \
	fi
	@echo "Local environment is ready:"
	@echo "  Web UI:  $(DEV_WEB_URL)"
	@echo "  Backend: $(DEV_API_URL)"
	@echo "  Scanner: $(DEV_SCANNER_URL)"
	@echo "Mock auth users:"
	@echo "  local-user  -> X-Mock-User-Id: local-user"
	@echo "  local-admin -> X-Mock-User-Id: local-admin"
	@echo "Logs:"
	@echo "  Backend: $(DEV_SERVER_LOG)"
	@echo "  Frontend: $(DEV_WEB_LOG)"

dev-server: ## 启动后端开发服务器
	cd server && /bin/sh -lc '$(DEV_SERVER_PREPARE) && exec $(DEV_SERVER_CMD)'

dev-server-restart: ## 重启后端开发服务器
	@mkdir -p $(DEV_DIR)
	@$(DEV_PROCESS) stop --pid-file $(DEV_SERVER_PID)
	@$(DEV_PROCESS) start --pid-file $(DEV_SERVER_PID) --log-file $(DEV_SERVER_LOG) --cwd server -- /bin/sh -lc '$(DEV_SERVER_PREPARE) && exec env $(DEV_SERVER_SCANNER_ENV) $(DEV_SERVER_CMD)' >/dev/null
	@echo "Waiting for backend on $(DEV_API_URL) ..."
	@for i in $$(seq 1 30); do \
		if curl -sf $(DEV_API_URL)/actuator/health >/dev/null; then \
			echo "Backend ready."; \
			exit 0; \
		fi; \
		sleep 2; \
	done; \
	echo "Backend failed to become ready. Check $(DEV_SERVER_LOG)"; \
	exit 1

namespace-smoke: ## 运行命名空间工作流 smoke test
	./scripts/namespace-smoke-test.sh $(DEV_API_URL)

dev-down: ## 停止本地开发环境（含 skill-scanner）
	$(DEV_COMPOSE) down --remove-orphans

dev-all-down: ## 停止本地开发环境（依赖 + scanner + 后端 + 前端）
	@$(DEV_PROCESS) stop --pid-file $(DEV_SERVER_PID)
	@$(DEV_PROCESS) stop --pid-file $(DEV_WEB_PID)
	@$(MAKE) dev-down

dev-all-reset: ## 重置本地开发环境（清理依赖数据卷后重新启动）
	@$(DEV_PROCESS) stop --pid-file $(DEV_SERVER_PID)
	@$(DEV_PROCESS) stop --pid-file $(DEV_WEB_PID)
	$(DEV_COMPOSE) down -v --remove-orphans
	rm -rf $(DEV_DIR)
	@$(MAKE) dev-all

dev-status: ## 查看本地开发服务状态
	@echo "=== Dependency Services ==="
	@$(DEV_COMPOSE) ps
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

build-backend: ## 构建后端
	cd server && ./mvnw clean package -DskipTests

test-backend: ## 运行后端单元测试
	cd server && JDK_JAVA_OPTIONS="$(BACKEND_TEST_JAVA_OPTIONS)" ./mvnw test

build-backend-app: ## 构建 skillhub-app 及其依赖模块
	cd server && ./mvnw -pl skillhub-app -am clean package -DskipTests

test-backend-app: ## 运行 skillhub-app 及其依赖模块测试
	cd server && JDK_JAVA_OPTIONS="$(BACKEND_TEST_JAVA_OPTIONS)" ./mvnw -pl skillhub-app -am test

build: build-backend build-frontend ## 完整构建前后端

test: test-backend test-frontend ## 运行前后端完整单元测试

check: build test ## 执行前后端完整构建和完整单元测试

clean: ## 清理构建产物
	cd server && ./mvnw clean
	$(DEV_COMPOSE) down -v
	rm -rf $(DEV_DIR)

generate-api: ## 生成 OpenAPI 类型（前端用）
	@echo "Generating OpenAPI types..."
	cd web && pnpm run generate-api

web-install: ## 安装前端依赖
	cd web && pnpm install

web-deps: ## 确保前端依赖可用（本地开发优先复用现有 node_modules）
	@if [ ! -d web/node_modules ]; then \
		echo "Installing frontend dependencies (node_modules missing)..."; \
		$(MAKE) web-install-ci; \
	elif [ ! -f web/node_modules/.modules.yaml ]; then \
		echo "Installing frontend dependencies (.modules.yaml missing)..."; \
		$(MAKE) web-install-ci; \
	elif [ web/pnpm-lock.yaml -nt web/node_modules/.modules.yaml ]; then \
		echo "Installing frontend dependencies (lockfile changed)..."; \
		$(MAKE) web-install-ci; \
	else \
		echo "Using existing frontend dependencies."; \
	fi

web-install-ci: ## 以 CI 方式安装前端依赖
	cd web && CI=true pnpm install --frozen-lockfile

dev-web: ## 启动前端开发服务器
	cd web && pnpm run dev

build-frontend: web-deps ## 构建前端
	cd web && pnpm run build

test-frontend: web-deps ## 运行前端单元测试
	cd web && pnpm run test

test-e2e-frontend: web-deps ## 运行前端 E2E 测试（Playwright）
	cd web && pnpm run test:e2e

test-e2e-smoke-frontend: web-deps ## 运行前端 E2E smoke 测试（Playwright）
	cd web && pnpm run test:e2e:smoke

build-web: build-frontend ## 构建前端

test-web: test-frontend ## 运行前端测试

typecheck-web: ## 前端类型检查
	cd web && pnpm run typecheck

lint-web: ## 前端代码检查
	cd web && pnpm run lint

db-reset: ## 重置数据库
	$(DEV_COMPOSE) down -v --remove-orphans
	$(DEV_COMPOSE) up -d --wait --remove-orphans postgres
	cd server && ./mvnw flyway:migrate -pl skillhub-app

validate-release-config: ## 校验发布环境变量文件（默认 .env.release）
	./scripts/validate-release-config.sh .env.release

staging: ## 构建并启动 staging 环境，运行 smoke test（混合模式：后端镜像 + 前端静态文件）
	@echo "=== [1/5] Building backend JAR and Docker image ==="
	cd server && ./mvnw package -DskipTests -B -q
	docker build -t $(STAGING_SERVER_IMAGE) -f server/Dockerfile.dev server
	@echo "=== [2/5] Building frontend static files ==="
	cd web && pnpm run build
	@echo "=== [3/5] Starting dependency services ==="
	$(STAGING_BASE_COMPOSE) up -d --wait
	@echo "=== [4/5] Starting staging services ==="
	$(STAGING_COMPOSE) up -d --wait server web
	@echo "=== [5/5] Running smoke tests ==="
	@if BOOTSTRAP_ADMIN_USERNAME=admin BOOTSTRAP_ADMIN_PASSWORD='Admin@staging2026' \
		bash scripts/smoke-test.sh $(STAGING_API_URL); then \
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
		$(STAGING_COMPOSE) logs server; \
		$(MAKE) staging-down; \
		exit 1; \
	fi

staging-down: ## 停止 staging 环境
	$(STAGING_COMPOSE) down --remove-orphans

staging-logs: ## 查看 staging 服务日志（SERVICE=server|web，默认 server）
	@SERVICE=$${SERVICE:-server}; \
	$(STAGING_COMPOSE) logs -f $$SERVICE

pr: ## 推送当前分支并创建 Pull Request（需要 gh CLI，仅限交互式终端）
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

parallel-init: ## 创建 Claude/Codex/integration 并行 worktree（TASK=<slug>）
	@if [ -z "$(TASK)" ]; then \
		echo "Usage: make parallel-init TASK=<task-slug> [PARALLEL_BASE_REF=origin/main] [PARALLEL_WORKTREE_ROOT=/path]"; \
		exit 1; \
	fi
	./scripts/parallel-init.sh "$(TASK)" "$(PARALLEL_BASE_REF)" "$(PARALLEL_WORKTREE_ROOT)"

parallel-sync: ## 在 integration worktree 合并 Claude/Codex 分支（自动识别当前 task）
	PARALLEL_WORKTREE_ROOT="$(PARALLEL_WORKTREE_ROOT)" ./scripts/parallel-sync.sh $(SOURCES)

parallel-up: ## 在 integration worktree 合并并启动联调环境（自动识别当前 task）
	PARALLEL_WORKTREE_ROOT="$(PARALLEL_WORKTREE_ROOT)" ./scripts/parallel-up.sh $(SOURCES)

parallel-down: ## 在 integration worktree 停止联调环境
	./scripts/parallel-down.sh

docs-dev: ## 启动文档开发服务器
	cd docs/skillhub && npm run dev

docs-build: ## 构建文档站点
	cd docs/skillhub && npm run build

docs-preview: ## 预览构建后的文档站点
	cd docs/skillhub && npm run preview
