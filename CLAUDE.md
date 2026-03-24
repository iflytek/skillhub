# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SkillHub is an enterprise-grade, self-hosted agent skill registry for publishing, discovering, and managing reusable skill packages. Built with Java 21 / Spring Boot 3.2.3 backend and React 19 / TypeScript / Vite frontend.

## Development Commands

All commands run from the project root via `Makefile`:

```bash
make dev-all              # Start full local stack (Postgres, Redis, MinIO, scanner, backend, frontend)
make dev-all-down         # Stop everything
make dev-all-reset        # Reset (clean volumes) and restart
make dev-status           # Check service status

make test                 # Run all backend + frontend tests
make test-backend         # Backend tests only
make test-backend-app     # skillhub-app + dependencies tests
make test-frontend        # Frontend tests only (Vitest)

make build                # Build backend + frontend
make build-backend-app    # Build skillhub-app + dependencies

make typecheck-web        # TypeScript type checking
make lint-web             # ESLint
make generate-api         # Regenerate OpenAPI types after backend API changes
make staging              # Build + staging environment + smoke tests
make pr                   # Push branch + create PR (requires gh CLI)
```

**Important**: Never run `./mvnw -pl skillhub-app clean test` directly — use `make test-backend-app` (which adds `-am`) to avoid stale artifact issues.

### Running a Single Backend Test

```bash
cd server && JDK_JAVA_OPTIONS="-XX:+EnableDynamicAgentLoading" ./mvnw -pl skillhub-app -am test -Dtest=YourTestClassName
```

### Running a Single Frontend Test

```bash
cd web && pnpm exec vitest run src/path/to/your-test.test.ts
```

### Local Access

- Web UI: http://localhost:3000
- Backend API: http://localhost:8080
- Mock users: `X-Mock-User-Id: local-user` (normal) / `X-Mock-User-Id: local-admin` (super admin)

## Architecture

### Backend Module Structure (Dependency Inversion)

```
server/
├── skillhub-app           # Boot entry, Controllers, global config, query repositories
├── skillhub-domain        # Core entities, domain services, Repository interfaces (innermost, no deps)
├── skillhub-auth          # OAuth2, RBAC, API tokens, session management (depends on domain)
├── skillhub-search        # Search SPI + PostgreSQL full-text impl (depends on domain)
├── skillhub-storage       # Object storage abstraction: LocalFile / S3 (independent SPI)
├── skillhub-infra         # JPA implementations of domain Repository interfaces (depends on domain)
└── skillhub-notification  # Notification system
```

**Key rule**: `domain` is the innermost layer — it defines interfaces only. `infra` implements them via Spring Data JPA. Never add `domain → infra` dependencies.

**App layer convention**: Controllers handle transport only. App Services orchestrate cross-domain workflows. Complex read-model joins go into query repositories under `skillhub-app/repository`.

### Frontend Structure

```
web/src/
├── api/generated/     # Auto-generated OpenAPI types (schema.d.ts) — do not edit manually
├── shared/ui/         # Shared UI components
└── ...                # Feature-based organization
```

- Package manager: pnpm
- Routing: TanStack Router
- Data fetching: TanStack Query
- Styling: Tailwind CSS + Radix UI
- i18n: i18next
- Database: PostgreSQL 16 with Flyway migrations
- Cache/Session: Redis 7

## Code Conventions

### Commit Messages

Conventional commit format: `type(scope): description`
- Types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`
- Example: `feat(auth): add local account login`
- **Never** add `Co-Authored-By` trailers unless explicitly requested
- Git commit authors must not contain model/tool CLI names (Claude Code, Codex, Gemini)

### Code Style

- **Backend**: Java 4-space indent, `PascalCase` classes, packages under `com.iflytek.skillhub.*`
- **Frontend**: TypeScript 2-space indent, no semicolons, PascalCase components, kebab-case filenames for features/tests (e.g., `skill-delete-flow.test.ts`)
- **Tests**: Backend `*Test.java` under `src/test/java`; Frontend `*.test.ts(x)` colocated or under `web/test/`

### API Contract Management

When backend API contracts change:
1. Run `make generate-api` to regenerate `web/src/api/generated/schema.d.ts`
2. Commit the updated file
3. Optionally run `./scripts/check-openapi-generated.sh` for strict drift checking

### Pull Request Checklist

- Backend tests pass
- Frontend typecheck/build passes (if frontend changed)
- API types regenerated (if backend API changed)
- PR description explains motivation, scope, and impact
- Don't mix refactoring with behavior changes

## Security

- Report security issues privately via GitHub Security Advisories
- Never commit secrets; respect `.gitignore` (`.env*`, `.dev/`, `node_modules/`)
- Start from `.env.release.example` for deployable config
