# SkillHub - Claude Code Instructions

## Project Overview

SkillHub is an enterprise-grade, self-hosted agent skill registry that enables teams to publish, discover, and manage reusable skill packages within their organization. It's built for on-premise deployment with full data sovereignty.

## Tech Stack

### Backend
- **Language**: Java 21
- **Framework**: Spring Boot 3.2.3
- **Architecture**: Multi-module Maven project with clean architecture
- **Modules**:
  - `skillhub-app`: Main application entry point
  - `skillhub-domain`: Core business logic
  - `skillhub-auth`: Authentication and authorization
  - `skillhub-search`: Search functionality
  - `skillhub-storage`: Storage abstraction layer
  - `skillhub-infra`: Infrastructure concerns
- **Database**: PostgreSQL 16 with Flyway migrations
- **Cache**: Redis 7
- **Storage**: S3/MinIO for skill packages

### Frontend
- **Language**: TypeScript
- **Framework**: React 19
- **Build Tool**: Vite
- **Routing**: TanStack Router
- **Data Fetching**: TanStack Query
- **Styling**: Tailwind CSS + Radix UI
- **API Client**: OpenAPI TypeScript (type-safe)
- **i18n**: i18next

### Infrastructure
- **Containerization**: Docker & Docker Compose
- **Monitoring**: Prometheus + Grafana
- **Deployment**: Kubernetes manifests available
- **CI/CD**: GitHub Actions

## Development Workflow

### Starting the Development Environment

```bash
# Start full local stack (backend + frontend + dependencies)
make dev-all

# Stop everything
make dev-all-down

# Reset and start from clean slate
make dev-all-reset
```

### Local Access
- Web UI: http://localhost:3000
- Backend API: http://localhost:8080

### Mock Users (Local Development)
- `local-user`: Normal user for publishing and namespace operations
- `local-admin`: Super admin for review and admin flows
- Use `X-Mock-User-Id` header in local development

### Common Commands

```bash
make help                    # Show all available commands
make test                    # Run backend tests
make typecheck-web          # TypeScript type checking
make build-web              # Build frontend
make generate-api           # Regenerate OpenAPI types
./scripts/check-openapi-generated.sh  # Verify API contract sync
./scripts/smoke-test.sh http://localhost:8080  # Run smoke tests
```

## Code Conventions

### Commit Messages
Use conventional commit format:
- `feat(scope): description` - New features
- `fix(scope): description` - Bug fixes
- `docs(scope): description` - Documentation changes
- `refactor(scope): description` - Code refactoring
- `test(scope): description` - Test changes
- `chore(scope): description` - Build/tooling changes

Examples:
- `feat(auth): add local account login`
- `fix(ops): align smoke test with csrf flow`
- `docs(deploy): clarify runtime image usage`

**IMPORTANT**: Never add `Co-Authored-By` trailers to commit messages unless explicitly requested by the user.

### Code Style
- **Backend**: Follow standard Java conventions, Spring Boot best practices
- **Frontend**: Follow TypeScript/React best practices, use functional components
- Keep changes focused - avoid mixing refactors with behavior changes
- Follow existing module boundaries
- Prefer backward-compatible changes unless explicitly allowed

### Testing
- Add or update tests when behavior changes
- Backend tests must pass before merging
- Frontend typecheck/build must pass when frontend files changed

### API Contract Management
- When backend API contracts change, regenerate the OpenAPI types:
  ```bash
  make generate-api
  ```
- Commit the updated `web/src/api/generated/schema.d.ts`
- Run `./scripts/check-openapi-generated.sh` for strict drift checking

## File Structure

```
skillhub/
├── server/                 # Backend (Java/Spring Boot)
│   ├── skillhub-app/      # Main application
│   ├── skillhub-domain/   # Core business logic
│   ├── skillhub-auth/     # Authentication
│   ├── skillhub-search/   # Search functionality
│   ├── skillhub-storage/  # Storage layer
│   └── skillhub-infra/    # Infrastructure
├── web/                   # Frontend (React/TypeScript)
│   ├── src/
│   │   ├── api/generated/ # Auto-generated API types
│   │   └── ...
│   └── ...
├── docs/                  # Documentation
├── scripts/               # Utility scripts
├── deploy/                # Deployment configs
├── monitoring/            # Prometheus + Grafana
├── Makefile              # Common tasks
└── docker-compose.yml    # Local development stack
```

## Important Guidelines

### Before Making Changes
1. Read relevant design docs in `docs/`
2. Open an issue for non-trivial changes before large PRs
3. Check existing patterns and conventions
4. Understand the module boundaries

### Pull Request Checklist
- Branch is rebased/merged cleanly from target branch
- Relevant backend tests pass
- Frontend typecheck/build passes (if frontend changed)
- API types regenerated (if backend API changed)
- Smoke coverage updated (if operator workflows changed)
- PR description explains motivation, scope, and impact

### What to Avoid
- Don't mix refactoring with behavior changes
- Don't break module boundaries
- Don't skip API contract regeneration
- Don't make breaking changes without explicit approval
- Don't open public issues for security vulnerabilities
- Don't ignore `.gitignore` rules or commit local/generated files such as `.env*`, `.dev/`, `node_modules/`, or `docs/superpowers/` unless the repo already tracks them on purpose

## Security
- Report security issues privately via GitHub Security Advisories
- Never commit secrets or credentials
- Use environment variables for configuration
- Follow OWASP best practices

## Documentation
- Full documentation: https://zread.ai/iflytek/skillhub
- Contributing guide: CONTRIBUTING.md
- Code of conduct: CODE_OF_CONDUCT.md

## License
Apache License 2.0
