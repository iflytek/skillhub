---
name: api-and-namespace-design
description: API design conventions, namespace coordinate system, RBAC roles, ClawHub compatibility layer, OpenAPI contract sync rules, and CSRF/session handling.
license: Apache-2.0
---

# API and Namespace Design Skill

## Trigger

Use this skill when:
- Adding or modifying REST API endpoints
- Changing namespace, skill, or user coordinate logic
- Working on ClawHub CLI compatibility layer
- Modifying OpenAPI specifications or generated types
- Adding new admin or governance endpoints

## Namespace Coordinate System

SkillHub uses a two-axis coordinate model:

```
@{namespace_slug}/{skill_slug}
```

- `@global/my-skill` — Global namespace skill
- `@my-team/my-skill` — Team namespace skill (namespace slug is any valid slug)
- `@department-ops/my-skill` — Department namespace skill

### Namespace Model

Namespaces (`domain/namespace/`):
- **Slug**: unique identifier, validated by `SlugValidator`
- **Status**: `ACTIVE`, `FROZEN`, `ARCHIVED`
- **Roles**: `OWNER`, `ADMIN`, `MEMBER`
- Frozen or archived namespaces cannot publish skills

### RBAC Roles

**Namespace-level** (`domain/namespace/NamespaceRole`):
- `OWNER` — Full control over namespace and all skills
- `ADMIN` — Can manage members, archive skills, publish
- `MEMBER` — Can publish skills to the namespace

**Platform-level**:
- `SUPER_ADMIN` — Bypasses all permission checks, can publish directly without review

## ClawHub Compatibility Layer

ClawHub CLI uses a single-slug model (no `/` allowed in slugs). Mapping:

| SkillHub Coordinate | Canonical Slug | Notes |
|---------------------|----------------|-------|
| `@global/my-skill` | `my-skill` | Global namespace omits prefix |
| `@team-name/my-skill` | `team-name--my-skill` | Double-dash separator |

**Conflict resolution**: `--` split takes priority. `@global/team-name--my-skill` would conflict
with `@team-name/my-skill`, resolved to the team namespace skill. Global skill slugs must NOT
contain `--`.

## API Design

### Controllers

- Controllers in `skillhub-app` (`com.iflytek.skillhub.controller/`) are **transport only**
- Responsibilities: extract auth context, bind request params, wrap responses
- Complex business logic belongs in domain services (`skillhub-domain`) or app services
- Use Springdoc OpenAPI annotations (`@Operation`, `@ApiResponse`) for API documentation
- User identity is always **String** in API inputs and outputs

### Request/Response Patterns

- DTOs in `com.iflytek.skillhub.dto/`
- `ReviewTaskRequest` / `ReviewTaskResponse` for review workflow
- Response wrapping handled at controller layer
- Validation errors use `DomainBadRequestException` with i18n message keys

### Session and CSRF

- Session-based auth with cookie storage
- CSRF protection via `XSRF-TOKEN` cookie and `X-XSRF-TOKEN` header
- Smoke tests validate the full register → login → CSRF → action → logout flow
- Mock auth uses `X-Mock-User-Id` header in local dev

### Well-known Discovery

`/.well-known/clawhub.json` returns `{ "apiBase": "/api/v1" }` for ClawHub CLI auto-discovery.

## OpenAPI Contract Sync

When backend API contracts change:

```bash
make generate-api
```

This runs `openapi-typescript http://localhost:8080/v3/api-docs -o src/api/generated/schema.d.ts`.

Commit the updated `web/src/api/generated/schema.d.ts` with the PR.

To verify no drift:

```bash
./scripts/check-openapi-generated.sh
```

This starts local dependencies, boots the backend, regenerates the schema, and fails if the
checked-in SDK is stale.

## Versioning and Tags

- Semantic versioning for skill versions (`major.minor.patch`)
- `latest` tag is system-reserved, read-only, auto-follows `Skill.latestVersionId`
- Custom tags (`stable`, `beta`) are manually maintained
- `latest` cannot be moved manually
- Auto-generated versions use `yyyyMMdd.HHmmss` format when no version is specified in SKILL.md

## Key API Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/v1/auth/me` | Current user info (401 if unauthenticated) |
| `POST` | `/api/v1/auth/local/login` | Local account login |
| `POST` | `/api/v1/auth/local/register` | Local account registration |
| `POST` | `/api/v1/auth/logout` | Logout (302/200/204) |
| `POST` | `/api/v1/auth/local/change-password` | Password change |
| `GET` | `/api/v1/namespaces` | List namespaces |
| `GET` | `/api/v1/labels` | List visible labels (public) |
| `POST` | `/api/v1/admin/labels` | Create label definition (admin) |
| `DELETE` | `/api/v1/admin/labels/{slug}` | Delete label definition (admin) |
| `GET` | `/actuator/health` | Health check |
| `GET` | `/actuator/prometheus` | Prometheus metrics |

## Common Pitfalls

- Forgetting CSRF token on POST/PUT/DELETE requests (needs `X-XSRF-TOKEN` header)
- Using numeric user IDs in API — all user identities are **String**
- Not regenerating OpenAPI types after adding/changing endpoints
- Putting business logic in controllers instead of domain/app services
- Assuming namespace slugs follow a specific prefix pattern — they are arbitrary valid slugs
