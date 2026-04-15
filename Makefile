.PHONY: help clean build test check \
	build-backend build-backend-docker test-backend build-backend-app test-backend-app \
	web-deps web-install web-install-ci \
	build-frontend test-frontend test-e2e-frontend test-e2e-smoke-frontend build-web test-web typecheck-web lint-web \
	generate-api validate-release-config pr

# GNU Make on Windows often runs recipes with cmd.exe, where ./mvnw is not valid.
ifeq ($(OS),Windows_NT)
MVNW := mvnw.cmd
WEB_INSTALL_CI_CMD = cd web && set "CI=true" && pnpm install --frozen-lockfile
else
MVNW := ./mvnw
WEB_INSTALL_CI_CMD = cd web && CI=true pnpm install --frozen-lockfile
endif

BACKEND_TEST_JAVA_OPTIONS ?= -XX:+EnableDynamicAgentLoading

help: ## Show help
	@grep -E '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-22s\033[0m %s\n", $$1, $$2}'

clean: ## Clean backend build outputs
	cd server && $(MVNW) clean

build-backend: ## Build backend (all modules)
	cd server && $(MVNW) clean package -DskipTests

BACKEND_DOCKER_IMAGE ?= skillhub-server:build-verify

build-backend-docker: ## Build backend Docker image (matches server/Dockerfile)
	docker build -t $(BACKEND_DOCKER_IMAGE) -f server/Dockerfile server

test-backend: ## Run backend unit tests
	cd server && JDK_JAVA_OPTIONS="$(BACKEND_TEST_JAVA_OPTIONS)" $(MVNW) test

build-backend-app: ## Build skillhub-app and its dependent modules
	cd server && $(MVNW) -pl skillhub-app -am clean package -DskipTests

test-backend-app: ## Run skillhub-app and its dependent module tests
	cd server && JDK_JAVA_OPTIONS="$(BACKEND_TEST_JAVA_OPTIONS)" $(MVNW) -pl skillhub-app -am test

web-install: ## Install frontend dependencies
	cd web && pnpm install

web-deps: ## Ensure frontend dependencies are installed
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

web-install-ci: ## Install frontend dependencies (CI mode)
	$(WEB_INSTALL_CI_CMD)

build-frontend: web-deps ## Build frontend
	cd web && pnpm run build

test-frontend: web-deps ## Run frontend unit tests
	cd web && pnpm run test

test-e2e-frontend: web-deps ## Run frontend E2E tests (Playwright)
	cd web && pnpm run test:e2e

test-e2e-smoke-frontend: web-deps ## Run frontend E2E smoke tests (Playwright)
	cd web && pnpm run test:e2e:smoke

build-web: build-frontend ## Build frontend (alias)

test-web: test-frontend ## Run frontend tests (alias)

typecheck-web: ## Typecheck frontend
	cd web && pnpm run typecheck

lint-web: ## Lint frontend
	cd web && pnpm run lint

build: build-backend build-frontend ## Build backend + frontend

test: test-backend test-frontend ## Run backend + frontend unit tests

check: build test ## Build + test

generate-api: ## Regenerate OpenAPI types for the web client
	cd web && pnpm run generate-api

validate-release-config: ## Validate release env file (default .env.release)
	./scripts/validate-release-config.sh .env.release

pr: ## Push branch and create a PR (requires gh CLI)
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
