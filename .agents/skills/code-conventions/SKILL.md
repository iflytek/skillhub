---
name: code-conventions
description: Code style, logging, and testing conventions for SkillHub backend (Java) and frontend (TypeScript). Use when writing or reviewing code.
license: Apache-2.0
---

# Code Conventions Skill

## Java / Backend Conventions

### User Identity Type

User identity is **always `String`** throughout the codebase. This covers:
- Authentication and authorization
- API parameters and responses
- Permission checks
- Audit logs
- Resource owner, creator, reviewer, actor, submittedBy fields

Never introduce `int`, `long`, or `bigint` as user identifiers. The platform needs to support
external SSO/OIDC/SCIM identity sources whose UIDs are typically stable strings.

### Exception Handling

- Use `LocalizedDomainException` for user-facing error messages (supports i18n)
- Use `DomainBadRequestException` for invalid client input
- Use `DomainNotFoundException` for missing resources
- Use `DomainForbiddenException` for authorization failures
- Exception classes live in `skillhub-domain/shared/exception/`

### Domain Services

- Return domain objects, not DTOs
- Contain business rules and state transitions
- Use domain events for cross-cutting side effects (publishing, notifications)
- Located in `domain/{submodule}/service/`

### Controllers

- Transport only: extract auth context, bind request params, wrap responses
- No business logic in controllers
- Located in `com.iflytek.skillhub.controller/`

### Query Repositories

- Handle read-model joins and presentation projection
- Return DTOs or presentation models
- Located in `com.iflytek.skillhub.repository/`
- Named like `*QueryRepository` (e.g., `GovernanceQueryRepository`, `MySkillQueryRepository`)

### App Services

- Workflow orchestration: coordinate domain services and query repositories
- Should express "what this endpoint does", not "how it assembles DTOs"
- Located in `com.iflytek.skillhub.service/`

### Logging

- Use SLF4J with structured logging
- Use MDC for request tracing
- Log at appropriate levels: INFO for business events, DEBUG for troubleshooting, ERROR for failures

## TypeScript / Frontend Conventions

### Type Safety

- Strict TypeScript mode. No `any` types.
- Use generated OpenAPI types from `web/src/api/generated/schema.d.ts` for all API interactions.
- Additional types in `web/src/types/`

### Data Fetching

- **Always use TanStack Query** (`@tanstack/react-query`) for server state
- **Never use `useEffect`** for data fetching
- Use `openapi-fetch` client for type-safe API calls

### Component Composition

- **Radix UI** primitives: `@radix-ui/react-dropdown-menu`, `@radix-ui/react-select`
- **class-variance-authority** (cva) for component variants
- **clsx** + **tailwind-merge** for class merging
- **`cn()` utility**: `web/src/shared/lib/utils.ts`
- shadcn/ui is NOT used as a library

### State Management

- **TanStack Query** for server state (API data, caching, invalidation)
- **Zustand** for local/UI state (theme, sidebar, modals, form state)

### Feature-Sliced Design

| Layer | Path | Purpose |
|-------|------|---------|
| Pages | `web/src/pages/` | Route-level page components |
| Features | `web/src/features/` | Self-contained business features |
| Entities | `web/src/entities/` | Domain entity display logic |
| Shared | `web/src/shared/` | Reusable UI components, hooks, utilities |

Place code at the lowest appropriate layer. Do not put page-level logic in shared.

### Styling

- Tailwind CSS for all styling
- Follow existing component patterns
- Use `cn()` for conditional class merging

### Internationalization

- Use i18next + react-i18next
- All user-facing text must be translatable
- Translation keys in `web/src/i18n/`

## Testing Philosophy

### Backend

- JUnit 5 + Mockito + AssertJ
- Use Spring Boot test slices where possible (`@WebMvcTest`, `@DataJpaTest`)
- Test behaviors, not implementations
- Use `make test-backend-app` (includes `-am` for dependent modules)
- Never run `./mvnw -pl skillhub-app clean test` directly — stale Maven cache causes misleading errors

### Frontend

- Vitest for unit tests
- Playwright for E2E tests
- Test component behavior and user interactions

## Common Pitfalls

- **Maven multi-module**: Always use `-am` flag or Makefile targets to include dependent modules
- **OpenAPI types**: Must regenerate and commit after API contract changes
- **String identity**: Never use numeric types for user identifiers
- **Controller business logic**: Move to domain service or app service
- **Complex read-models in app service**: Extract to query repository
