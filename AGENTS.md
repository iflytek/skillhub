# SkillHub — AGENTS.md

**SkillHub** is an **enterprise-grade, self-hosted agent skill registry** for publishing,
discovering, and managing reusable skill packages across an organization. It provides a **REST API
backend**, a **React web UI**, a **security scanner**, and a **ClawHub CLI compatibility layer**.

## Quick Reference

| Item       | Value                                                      |
|------------|------------------------------------------------------------|
| Backend    | Spring Boot 3.2.3, Java 21, Maven multi-module (7 modules) |
| Frontend   | React 19, TypeScript, Vite, pnpm                           |
| Scanner    | Python (FastAPI), port 8000                                |
| Database   | PostgreSQL 16 (Flyway migrations)                          |
| Cache      | Redis 7 (sessions, distributed locks, idempotency)         |
| Storage    | LocalFile (dev) / S3/MinIO (prod)                          |
| Build      | `make dev-all` (dev), `make staging` (pre-PR)              |
| Docs       | `docs/` (design), `document/` (VitePress user guide)       |
| CI         | GitHub Actions (`.github/workflows/`)                      |

## Directory Map

```
skillhub/
├── server/                          # Maven multi-module Spring Boot backend
│   ├── skillhub-app/                # Application layer: bootstrap, controllers, assembly
│   │   ├── bootstrap/               # Bootstrap admin & local dev data initializers
│   │   ├── compat/                  # ClawHub CLI compatibility layer controllers
│   │   ├── config/                  # Spring configuration classes
│   │   ├── controller/              # REST controllers (transport only)
│   │   │   ├── admin/               # Admin controllers (user mgmt, labels, search)
│   │   │   ├── portal/              # Portal controllers (skills, governance, security)
│   │   │   └── support/             # Package extractors (zip, multipart)
│   │   ├── dto/                     # Request/response DTOs
│   │   ├── exception/               # Exception handling
│   │   ├── filter/                  # Servlet filters (auth context, rate limiting)
│   │   ├── listener/                # Event listeners (notification recipients, etc.)
│   │   ├── metrics/                 # Micrometer metrics
│   │   ├── projection/              # Lifecycle projection models
│   │   ├── ratelimit/               # Rate limiting logic
│   │   ├── repository/              # Query repositories (read-model assembly)
│   │   ├── security/                # Security configuration
│   │   ├── service/                 # App services (workflow orchestration)
│   │   ├── stream/                  # SSE streaming endpoints
│   │   ├── task/                    # Background task scheduling
│   │   └── SkillhubApplication.java # Spring Boot entry point
│   │
│   ├── skillhub-domain/             # Domain layer: entities, rules, services (innermost)
│   │   ├── audit/                   # AuditLog entity, repository, service
│   │   ├── auth/                    # Password reset entities
│   │   ├── event/                   # Domain event classes (SkillPublishedEvent, etc.)
│   │   ├── governance/              # Governance notification service
│   │   ├── idempotency/             # Idempotency records
│   │   ├── label/                   # Skill label management
│   │   ├── namespace/               # Namespace, members, roles, policies
│   │   ├── report/                  # Skill reporting/governance
│   │   ├── review/                  # Review tasks, promotion requests
│   │   ├── security/                # Security scanning domain model
│   │   ├── shared/                  # Shared domain utilities
│   │   │   └── exception/           # Domain exceptions (LocalizedDomainException, etc.)
│   │   ├── skill/                   # Core skill entities and services
│   │   │   ├── metadata/            # SKILL.md frontmatter parsing
│   │   │   ├── service/             # Skill domain services (publish, query, governance)
│   │   │   └── validation/          # Package validation (SkillPackagePolicy, etc.)
│   │   ├── social/                  # Star, rating, subscription entities
│   │   └── user/                    # UserAccount, profile moderation
│   │
│   ├── skillhub-auth/               # Authentication & authorization
│   │   ├── config/                  # Spring Security configuration
│   │   ├── device/                  # OAuth Device Flow for CLI auth
│   │   ├── identity/                # Identity binding service
│   │   ├── local/                   # Local (password) auth
│   │   ├── merge/                   # Account merging
│   │   ├── oauth/                   # OAuth2 login handlers
│   │   ├── policy/                  # Route security policies
│   │   ├── rbac/                    # RBAC service and role definitions
│   │   ├── token/                   # API token management
│   │   └── user/                    # User-related auth services
│   │
│   ├── skillhub-search/             # Search SPI + PostgreSQL full-text implementation
│   │   ├── postgres/                # PostgresFullTextIndexService, QueryService
│   │   └── service/                 # Search SPI interfaces
│   │
│   ├── skillhub-storage/            # Object storage SPI
│   │   ├── local/                   # LocalFileStorageService
│   │   └── s3/                      # S3StorageService (AWS SDK v2)
│   │
│   ├── skillhub-infra/              # Infrastructure: JPA repos, utilities
│   │   └── repository/              # Spring Data JPA repository implementations
│   │
│   ├── skillhub-notification/       # Notification service (SSE, email)
│   │   ├── domain/                  # Notification domain model
│   │   ├── service/                 # Notification delivery services
│   │   └── sse/                     # SSE endpoint support
│   │
│   ├── Dockerfile.dev               # Dockerfile for staging builds
│   ├── Dockerfile                   # Production multi-stage build
│   ├── pom.xml                      # Parent POM (Spring Boot 3.2.3 parent)
│   └── scripts/
│       └── run-dev-app.sh           # Local dev startup script
│
├── web/                             # React frontend (Vite + pnpm)
│   ├── src/
│   │   ├── api/                     # OpenAPI-generated types + fetch client
│   │   │   └── generated/
│   │   │       └── schema.d.ts      # Generated OpenAPI types (CHECKED IN)
│   │   ├── app/                     # Router, layout, global providers
│   │   ├── docs/                    # In-app documentation pages
│   │   ├── entities/                # Domain entity display logic
│   │   │   ├── skill/               # Skill card, detail components
│   │   │   ├── user/                # User profile components
│   │   │   └── namespace/           # Namespace display components
│   │   ├── features/                # Business feature modules
│   │   │   ├── admin/               # Admin panel features
│   │   │   ├── auth/                # Login, OAuth flows
│   │   │   ├── governance/          # Skill governance actions
│   │   │   ├── namespace/           # Namespace management
│   │   │   ├── notification/        # User notifications
│   │   │   ├── promotion/           # Skill promotion workflows
│   │   │   ├── publish/             # Skill upload/publish UI
│   │   │   ├── report/              # Skill reporting
│   │   │   ├── review/              # Review workflow UI
│   │   │   ├── search/              # Skill search and filtering
│   │   │   ├── security-audit/      # Security audit viewer
│   │   │   ├── skill/               # Skill detail, listing
│   │   │   ├── social/              # Stars, ratings, subscriptions
│   │   │   └── token/               # API token management
│   │   ├── i18n/                    # Internationalization
│   │   ├── pages/                   # Route-level page components
│   │   ├── shared/                  # Shared UI, hooks, utilities
│   │   │   ├── components/          # Reusable UI components
│   │   │   ├── hooks/               # Custom React hooks
│   │   │   ├── lib/
│   │   │   │   └── utils.ts         # cn() class merging utility
│   │   │   └── ui/                  # Radix UI-based primitives
│   │   └── types/                   # Additional TypeScript types
│   ├── e2e/                         # Playwright E2E tests
│   ├── nginx.conf.template          # Nginx runtime config template
│   ├── Dockerfile                   # Multi-stage build (Node → Nginx)
│   └── package.json                 # Dependencies (React 19, TanStack Query, Radix UI, etc.)
│
├── scanner/                         # Security scanner (Python/FastAPI)
│   ├── docs/                        # Scanner documentation
│   ├── examples/                    # Example scan inputs/outputs
│   └── Dockerfile                   # Scanner container build
│
├── docs/                            # Design documents (source of truth)
│   ├── prds/                        # Product requirement documents
│   ├── skillhub/                    # VitePress user guide source
│   └── superpowers/                 # Internal tooling docs
│
├── document/                        # VitePress documentation site (published)
│   ├── docs/                        # Markdown documentation
│   ├── src/                         # VitePress theme
│   └── i18n/                        # Internationalization
│
├── deploy/k8s/                      # Kubernetes manifests (basic)
├── monitoring/                      # Prometheus + Grafana stack
├── scripts/                         # Build, test, and deployment scripts
│   ├── smoke-test.sh                # Basic API smoke test
│   ├── namespace-smoke-test.sh      # Namespace workflow smoke test
│   ├── governance-smoke-test.sh     # Governance flow smoke test
│   ├── promotion-smoke-test.sh      # Promotion flow smoke test
│   ├── check-openapi-generated.sh   # Verify OpenAPI SDK is not stale
│   ├── validate-release-config.sh   # Validate release env configuration
│   ├── dev-process.sh               # Local process manager (PID-based)
│   ├── runtime.sh                   # Runtime deployment script
│   ├── parallel-init.sh             # Parallel worktree initialization
│   ├── parallel-sync.sh             # Merge worktrees in integration branch
│   ├── parallel-up.sh               # Merge + start dev environment
│   ├── parallel-down.sh             # Stop parallel dev environment
│   └── prepare-pr-batch.sh          # Batch PR preparation
│
├── .github/
│   ├── workflows/                   # GitHub Actions CI/CD
│   ├── ISSUE_TEMPLATE/              # Issue templates
│   └── scripts/                     # Deno scripts for triage, release notes, rewards
│
├── AGENTS.md                        # AI agent rules (this file)
├── .agents/skills/                  # Focused AI skill definitions
├── Makefile                         # Top-level build/test/dev orchestration
├── docker-compose.yml               # Local dev dependency services
├── compose.release.yml              # Production release compose file
├── CONTRIBUTING.md                  # Contribution guidelines
├── CODE_OF_CONDUCT.md               # Community standards
└── README.md                        # Project overview
```

**Key Locations for Common Tasks:**

| Task | Where to Look |
|------|---------------|
| Add REST endpoint | `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/` |
| Add domain entity/service | `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/` |
| Add auth logic | `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/` |
| Add search logic | `server/skillhub-search/src/main/java/com/iflytek/skillhub/search/` |
| Add query repository | `server/skillhub-app/src/main/java/com/iflytek/skillhub/repository/` |
| Change RBAC/roles | `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/rbac/` |
| Change skill validation | `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/validation/` |
| Add frontend page | `web/src/pages/` |
| Add frontend feature | `web/src/features/` |
| Add shared component | `web/src/shared/components/` |
| Change API contract | Backend controller → run `make generate-api` → commit generated file |
| Add smoke test | `scripts/` (new `.sh` file) |
| Add E2E test | `web/e2e/` (Playwright) |
| Add backend test | `server/skillhub-*/src/test/java/` (alongside source module) |

## Critical Rules

### Do Not Manually Edit Generated Files

- `web/src/api/generated/schema.d.ts` — regenerated via `make generate-api`
- `document/docs/` — auto-generated user documentation (VitePress)
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/` — some DTOs may be generated

### After Making Changes

**Backend changes:**
- Edit Java code → `make dev-server-restart` (local dev)
- Add/modify controller → `make generate-api` to regenerate frontend types
- Add/modify domain service → `make test-backend-app` to verify tests

**Frontend changes:**
- Edit TypeScript/React → Vite HMR handles reload automatically
- After `make generate-api` → commit updated `web/src/api/generated/schema.d.ts`

**Always run before PR:**
```bash
make test-backend-app   # Backend tests (with dependent modules)
make typecheck-web      # Frontend type check
make lint-web           # Frontend lint
make staging            # Full staging regression + smoke test
```

### File-Specific Requirements

- **Controllers** (`skillhub-app/controller/`) are transport only: extract auth context,
  bind request params, wrap responses. No business logic.
- **App Services** (`skillhub-app/service/`) orchestrate workflows. Do not embed complex
  read-model assembly here — extract to query repositories.
- **Query Repositories** (`skillhub-app/repository/`) handle read-model joins and presentation
  projection. Named like `*QueryRepository`.
- **Domain Services** (`skillhub-domain/*/service/`) contain business rules and state transitions.
  Return domain objects, not DTOs.
- **Repository Interfaces** are defined in `skillhub-domain`, implemented in `skillhub-infra`.
- **Domain Exceptions** use `LocalizedDomainException` for user-facing messages with i18n keys.
- **Package-info files** (`package-info.java`) should exist for all packages.

## Development Workflow

### Build & Start

```bash
make dev-all          # Start full stack: Postgres, Redis, MinIO, backend, frontend
make dev-all-down     # Stop everything
make dev-all-reset    # Full reset (clears data volumes)
make dev-status       # Check service status
make dev-server-restart  # Restart backend after Java changes
```

**Access points:**
- Web UI: `http://localhost:3000`
- Backend API: `http://localhost:8080`
- Scanner: `http://localhost:8000`

**Local mock users** (no password needed):

| User ID | Role | Header |
|---------|------|--------|
| `local-user` | Regular user | `X-Mock-User-Id: local-user` |
| `local-admin` | Super admin | `X-Mock-User-Id: local-admin` |

**Bootstrap admin** (password-based, local profile):
- Username: `admin` / Password: `ChangeMe!2026`
- Disable with `BOOTSTRAP_ADMIN_ENABLED=false`

### Lint & Format

```bash
# Backend: enforced by Maven build (no separate lint target)
# Frontend:
make lint-web           # ESLint check
make typecheck-web      # TypeScript check
```

### Testing

```bash
make test-backend-app         # Backend unit tests (skillhub-app + dependencies)
make test-backend             # All backend module tests
make test-frontend            # Frontend unit tests (Vitest)
make test-e2e-frontend        # Frontend E2E tests (Playwright)
make test-e2e-smoke-frontend  # Frontend E2E smoke tests
./scripts/smoke-test.sh       # API smoke test
make namespace-smoke          # Namespace workflow smoke test
```

### Staging (Pre-PR Regression)

```bash
make staging          # Build backend Docker image + frontend static + smoke test
make staging-down     # Tear down
SERVICE=web make staging-logs  # View Nginx logs
```

Staging validates the containerized deployment path:
- Backend: built as Docker image from local source
- Frontend: built as static files, served by Nginx
- Dependencies: same Postgres/Redis/MinIO as local dev

### Parallel Agent Workflow

For parallel development with isolated worktrees:

```bash
make parallel-init TASK=feature-name
```

Creates dedicated Claude, Codex, and integration worktrees as sibling directories.
See `docs/13-parallel-workflow.md` for details.

## PR Submission

### PR Title Format

Use conventional commit style:

```
<type>(<scope>): <description>
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `test`: Adding or updating tests
- `refactor`: Code restructuring (no behavior change)
- `chore`: Build, CI, or maintenance tasks

**Scopes:** Module or domain name (e.g., `auth`, `search`, `publish`, `review`, `namespace`)

**Examples:**
```
feat(auth): add local account login
fix(publish): resolve null pointer in skill validation
docs(deploy): clarify runtime image usage
test(namespace): add membership service tests
refactor(review): extract query repository for governance list
chore(ci): add parallel workflow scripts
```

### Pre-PR Checklist

- [ ] Backend tests pass: `make test-backend-app`
- [ ] Frontend typecheck passes: `make typecheck-web`
- [ ] If API changed: `make generate-api` was run and `web/src/api/generated/schema.d.ts` is committed
- [ ] Smoke test passes: `make staging`
- [ ] Follow existing module boundaries and dependency direction
- [ ] Add/update tests for new behavior
- [ ] Update docs when APIs, auth flows, deployment, or operator workflows change

## Core Concepts

### Backend Clean Architecture

```
app → domain, auth, search, storage, infra, notification
infra → domain          # implements domain repository interfaces
auth → domain
search → domain
notification → domain
storage → (independent) # pure SPI
```

**Design intent**: `skillhub-domain` is the innermost layer. It defines entities, repository
interfaces, and domain services without depending on infra, auth, search, or storage.

**Code reality**: `skillhub-domain` declares a Maven dependency on `skillhub-storage`, and several
domain services (`SkillHardDeleteService`, `SkillDownloadService`, `SkillPublishService`,
`SkillGovernanceService`, `SkillQueryService`, `SkillStorageDeletionCompensationService`) import
`com.iflytek.skillhub.storage.ObjectStorageService`. This is an existing deviation from the ideal.
New code should avoid adding further cross-module dependencies from domain.

### Repository / Query Boundary

When adding new read logic, follow these rules:

1. **Domain repository ports** (`skillhub-domain`): Aggregate reads, state transitions, rule
   evaluation. Used by domain services.
2. **App query repositories** (`com.iflytek.skillhub.repository`): Read-model assembly that joins
   multiple sources, presentation projection. Used by controllers and app services.
3. **App services** (`com.iflytek.skillhub.service`): Workflow orchestration. Should express "what
   this endpoint does", not "how it assembles DTOs".
4. **Direct SQL / EntityManager**: Only when necessary, with class-level comment explaining why.

**Do not** add complex read-model assembly logic inside app services. Extract it into a query
repository when it joins multiple sources, does presentation projection, or is reused across services.

### Skill Lifecycle

`SkillVersionStatus` values: `DRAFT`, `SCANNING`, `SCAN_FAILED`, `UPLOADED`, `PENDING_REVIEW`,
`PUBLISHED`, `REJECTED`, `YANKED`.

`SkillStatus` enum values: `ACTIVE`, `HIDDEN`, `ARCHIVED`.

The design doc (`docs/14-skill-lifecycle.md`) specifies that `hidden` should be treated as a
governance overlay rather than a lifecycle state. The current code still defines
`SkillStatus.HIDDEN` in the enum. Follow the design doc's intent for new code.

**Key transitions:**
- Normal user first upload → `PENDING_REVIEW` (no initial DRAFT)
- SUPER_ADMIN first upload → `PUBLISHED` (direct publish)
- Review approve → `PENDING_REVIEW` → `PUBLISHED` (updates `latestVersionId`)
- Review reject → `PENDING_REVIEW` → `REJECTED`
- Withdraw review → `PENDING_REVIEW` → `UPLOADED` (also deletes pending review_task)
- Yank → `PUBLISHED` → `YANKED` (must recalculate `latestVersionId`)
- Hide/restore → independent `hidden` flag (governance overlay)
- Archive/Unarchive → `ACTIVE` ↔ `ARCHIVED` (container state)

### Namespace Coordinate System

SkillHub uses `@{namespace_slug}/{skill_slug}`:
- `@global/my-skill` — Platform-level public namespace
- `@my-team/my-skill` — Team/department namespace

ClawHub CLI compatibility maps:
| SkillHub | Canonical Slug |
|----------|---------------|
| `@global/my-skill` | `my-skill` |
| `@team-name/my-skill` | `team-name--my-skill` |

### Authentication

- Web: OAuth2 (GitHub) + local password auth
- CLI: OAuth Device Flow (web authorization → CLI credentials)
- Programmatic: API tokens (prefix-based secure hashing)
- Session: Spring Session + Redis

### RBAC

Platform roles: `SUPER_ADMIN`, `SKILL_ADMIN`, `USER_ADMIN`, `AUDITOR`
Namespace roles: `OWNER`, `ADMIN`, `MEMBER`

### Skill Package Protocol

- Root: `SKILL.md` with YAML frontmatter (`name`, `description` required)
- Allowed extensions (50+ types): `.md`, `.txt`, `.json`, `.yaml`, `.yml`, `.js`, `.ts`, `.py`,
  `.sh`, `.png`, `.jpg`, `.svg`, and many more (see `SkillPackagePolicy.ALLOWED_EXTENSIONS`)
- Limits: 10MB per file, 100MB total, 500 files max
- File type signatures validated (PNG magic bytes, SVG content check, etc.)

### Frontend State Management

- **TanStack Query** (`@tanstack/react-query`): All server state (API data)
- **Zustand**: Local/UI state (theme, sidebar, modals)
- **Never** use `useEffect` for data fetching

### Frontend Component Composition

- **Radix UI** primitives: `@radix-ui/react-dropdown-menu`, `@radix-ui/react-select`
- **class-variance-authority** (cva) for component variants
- **clsx** + **tailwind-merge** for class merging
- **`cn()` utility**: `web/src/shared/lib/utils.ts`
- shadcn/ui is NOT used as a library — only Radix primitives + utility composition

## Common Patterns

### Code Style

**Java:**
- User identity type is always `String` throughout the codebase
- Use Java 21 features (records, pattern matching, virtual threads)
- Follow existing naming patterns in the domain layer
- Error strings for `DomainBadRequestException`, etc. should be clear and actionable

**TypeScript:**
- Strict mode. No `any` types.
- Use generated OpenAPI types for all API interactions.
- Feature-Sliced Design: place code at the lowest appropriate layer.

### Testing Philosophy

- Backend: JUnit 5 + Mockito + AssertJ
- Frontend: Vitest for unit tests, Playwright for E2E
- **Use `make test-backend-app`** (includes `-am` for dependent modules) — never run
  `./mvnw -pl skillhub-app clean test` directly, as it can use stale Maven cache artifacts
- Test behaviors, not implementations
- Use Spring Boot test slices where possible (`@WebMvcTest`, `@DataJpaTest`)

### Frontend Testing

```bash
make test-frontend            # Vitest unit tests
make test-e2e-frontend        # Playwright E2E
make test-e2e-smoke-frontend  # Playwright smoke (subset of E2E)
```

### Logging Conventions

- **Backend**: SLF4J + Spring Boot logging. Use structured logging with MDC for request tracing.
- **Frontend**: `console.error` for errors, `console.warn` for deprecations, avoid `console.log` in production code.
- **Scanner**: Python logging module with structured JSON output.

### Security

- API tokens are stored as prefix-based secure hashes, never in plaintext
- OAuth2 client secrets and other secrets must not be logged or committed
- User identity is `String` (supports external SSO/OIDC/SCIM identity sources)
- The bootstrap admin (`BOOTSTRAP_ADMIN_ENABLED`) is for zero-config quickstart only

## Search Tips

```bash
# Find all REST endpoints
rg "@(Get|Post|Put|Delete|Patch)Mapping" --type java

# Find domain services
rg "class.*Service" server/skillhub-domain/

# Find query repositories
rg "QueryRepository" server/skillhub-app/

# Find controllers
rg "@RestController" server/skillhub-app/

# Find RBAC role checks
rg "@PreAuthorize" server/skillhub-app/

# Find skill validation logic
rg "SkillPackage" server/skillhub-domain/

# Find frontend features
rg "export" web/src/features/

# Find OpenAPI type generation script
rg "generate-api" web/package.json

# Find event listeners
rg "@EventListener" server/
```

## Design Philosophy

- **Hub first**: The server-side registry is the core product; CLI and agent integrations are entry capabilities
- **Compatibility first**: Support `SKILL.md` format and common directory conventions
- **Layered architecture**: Search and object storage must have replaceable boundaries (SPI pattern)
- **Open authentication**: OAuth2-based, extensible to multiple providers beyond GitHub
- **Audit first**: Enterprise distribution requires audit trails for publish, download, delete, and authorization

## References

### Essential Files
- **`Makefile`** — All build/test/dev automation targets
- **`CONTRIBUTING.md`** — Contribution guidelines and commit style
- **`CODE_OF_CONDUCT.md`** — Community standards
- **`server/pom.xml`** — Maven parent POM, module definitions, dependency versions
- **`web/package.json`** — Frontend dependencies and scripts
- **`.github/workflows/pr-tests.yml`** — PR test pipeline
- **`.github/workflows/publish-images.yml`** — Docker image publish to GHCR

### Key Directories
- **`server/skillhub-domain/`** — Core domain (entities, services, rules)
- **`server/skillhub-app/controller/`** — REST API endpoints
- **`server/skillhub-app/repository/`** — Query repositories
- **`server/skillhub-app/compat/`** — ClawHub CLI compatibility layer
- **`server/skillhub-auth/`** — Authentication and authorization
- **`web/src/features/`** — Frontend feature modules
- **`web/src/api/generated/`** — Generated OpenAPI types
- **`docs/`** — Design documents
- **`scripts/`** — Build, test, and deployment scripts

### Important Scripts
- **`scripts/smoke-test.sh`** — Basic API smoke test
- **`scripts/namespace-smoke-test.sh`** — Namespace workflow test
- **`scripts/check-openapi-generated.sh`** — Verify frontend SDK is current
- **`scripts/validate-release-config.sh`** — Validate production env config
- **`scripts/dev-process.sh`** — Local process manager (PID-based lifecycle)
- **`scripts/parallel-init.sh`** — Create isolated worktrees for parallel development

### Design Documents
- **`00-product-direction.md`** — Product positioning, MVP scope, coordinate system
- **`01-system-architecture.md`** — System architecture, module structure, dependency rules
- **`02-domain-model.md`** — Domain entities and relationships
- **`03-authentication-design.md`** — OAuth2, CLI Device Flow, API tokens
- **`04-search-architecture.md`** — Search SPI and implementations
- **`05-business-flows.md`** — Business process flows
- **`06-api-design.md`** — API contract specifications
- **`07-skill-protocol.md`** — SKILL.md format, package structure, CLI compatibility
- **`08-frontend-architecture.md`** — Frontend patterns and conventions
- **`14-skill-lifecycle.md`** — Skill state model (authoritative)
- **`dev-workflow.md`** — Local development workflow guide

### External Resources
- **SkillHub Docs**: https://iflytek.github.io/skillhub/
- **DeepWiki**: https://deepwiki.com/iflytek/skillhub
- **Discord**: https://discord.gg/qHYvtDNPHS
- **OpenSkills**: https://agents.md/ (skill package format reference)
- **OpenClaw**: https://github.com/openclaw/openclaw (CLI compatibility)
- **AstronClaw**: https://agent.xfyun.cn/astron-claw (cloud AI assistant integration)
