.PHONY: help dev dev-all dev-down dev-all-down dev-all-reset dev-logs dev-status build test clean web-install dev-server dev-web build-web test-web typecheck-web lint-web generate-api db-reset validate-release-config

DEV_DIR := .dev
DEV_SERVER_PID := $(DEV_DIR)/server.pid
DEV_WEB_PID := $(DEV_DIR)/web.pid
DEV_SERVER_LOG := $(DEV_DIR)/server.log
DEV_WEB_LOG := $(DEV_DIR)/web.log
DEV_WEB_URL := http://localhost:3000
DEV_API_URL := http://localhost:8080
DEV_PROCESS := python3 scripts/dev_process.py

help: ## 显示帮助
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-15s\033[0m %s\n", $$1, $$2}'

dev: ## 启动本地开发环境（仅依赖服务）
	docker compose up -d --wait --remove-orphans
	@echo "Services ready."
	@echo "Start backend with: make dev-server"
	@echo "Start frontend with: make dev-web"

dev-all: ## 一键启动本地开发环境（依赖 + 后端 + 前端）
	@mkdir -p $(DEV_DIR)
	@$(MAKE) dev
	@if [ ! -d web/node_modules ]; then \
		echo "Installing frontend dependencies..."; \
		$(MAKE) web-install; \
	fi
	@if $(DEV_PROCESS) status --pid-file $(DEV_SERVER_PID) >/dev/null 2>&1; then \
		echo "Backend already running with PID $$(cat $(DEV_SERVER_PID))"; \
	else \
		echo "Starting backend..."; \
		$(DEV_PROCESS) start --pid-file $(DEV_SERVER_PID) --log-file $(DEV_SERVER_LOG) --cwd server -- /bin/sh -lc './mvnw -pl skillhub-app -am install -DskipTests >/dev/null && exec ./mvnw -pl skillhub-app spring-boot:run -Dspring-boot.run.profiles=local' >/dev/null; \
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
			$(DEV_PROCESS) start --pid-file $(DEV_SERVER_PID) --log-file $(DEV_SERVER_LOG) --cwd server -- /bin/sh -lc './mvnw -pl skillhub-app -am install -DskipTests >/dev/null && exec ./mvnw -pl skillhub-app spring-boot:run -Dspring-boot.run.profiles=local' >/dev/null; \
		fi; \
	done; \
	if [ "$$backend_ready" -ne 1 ]; then \
		echo "Backend failed to become ready. Check $(DEV_SERVER_LOG)"; \
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
	@echo "Mock auth users:"
	@echo "  local-user  -> X-Mock-User-Id: local-user"
	@echo "  local-admin -> X-Mock-User-Id: local-admin"
	@echo "Logs:"
	@echo "  Backend: $(DEV_SERVER_LOG)"
	@echo "  Frontend: $(DEV_WEB_LOG)"

dev-server: ## 启动后端开发服务器
	cd server && /bin/sh -lc './mvnw -pl skillhub-app -am install -DskipTests >/dev/null && exec ./mvnw -pl skillhub-app spring-boot:run -Dspring-boot.run.profiles=local'

dev-down: ## 停止本地开发环境
	docker compose down --remove-orphans

dev-all-down: ## 停止本地开发环境（依赖 + 后端 + 前端）
	@$(DEV_PROCESS) stop --pid-file $(DEV_SERVER_PID)
	@$(DEV_PROCESS) stop --pid-file $(DEV_WEB_PID)
	@$(MAKE) dev-down

dev-all-reset: ## 重置本地开发环境（清理依赖数据卷后重新启动）
	@$(DEV_PROCESS) stop --pid-file $(DEV_SERVER_PID)
	@$(DEV_PROCESS) stop --pid-file $(DEV_WEB_PID)
	docker compose down -v --remove-orphans
	rm -rf $(DEV_DIR)
	@$(MAKE) dev-all

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

build: ## 构建后端
	cd server && ./mvnw clean package -DskipTests

test: ## 运行后端测试
	cd server && ./mvnw test

clean: ## 清理构建产物
	cd server && ./mvnw clean
	docker compose down -v
	rm -rf $(DEV_DIR)

generate-api: ## 生成 OpenAPI 类型（前端用）
	@echo "Generating OpenAPI types..."
	cd web && pnpm run generate-api

web-install: ## 安装前端依赖
	cd web && pnpm install

dev-web: ## 启动前端开发服务器
	cd web && pnpm run dev

build-web: ## 构建前端
	cd web && pnpm run build

test-web: ## 运行前端测试
	cd web && pnpm run test

typecheck-web: ## 前端类型检查
	cd web && pnpm run typecheck

lint-web: ## 前端代码检查
	cd web && pnpm run lint

db-reset: ## 重置数据库
	docker compose down -v --remove-orphans
	docker compose up -d --wait --remove-orphans postgres
	cd server && ./mvnw flyway:migrate -pl skillhub-app

validate-release-config: ## 校验发布环境变量文件（默认 .env.release）
	./scripts/validate-release-config.sh .env.release
