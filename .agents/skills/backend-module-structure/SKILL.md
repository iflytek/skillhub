---
name: backend-module-structure
description: Rules for the SkillHub backend Maven multi-module clean architecture. Ensures agents place new code in the correct module and respect dependency direction.
license: Apache-2.0
---

# Backend Module Structure Skill

## Trigger

Use this skill when:
- Adding or modifying Java backend code
- Creating new services, controllers, repositories, or entities
- Refactoring backend code across files
- Reviewing backend code placement

## Rules

### Dependency Direction

The design-doc dependency direction:

```
app → domain, auth, search, storage, infra, notification
infra → domain          # implements domain repository interfaces
auth → domain
search → domain
notification → domain
storage → (independent) # pure SPI
```

**Design intent**: `skillhub-domain` should be the innermost layer, defining entities,
repository interfaces, and domain services without depending on infra, auth, search, or storage.

**Code reality**: `skillhub-domain` declares a Maven dependency on `skillhub-storage` (via
`pom.xml`), and several domain services (`SkillHardDeleteService`, `SkillDownloadService`,
`SkillPublishService`, `SkillGovernanceService`, `SkillQueryService`,
`SkillStorageDeletionCompensationService`) import `com.iflytek.skillhub.storage.ObjectStorageService`.
This is an existing deviation from the ideal clean architecture. New code should avoid adding
further cross-module dependencies from domain.

### Where to Place Code

| Code Type | Module | Java Package |
|-----------|--------|-------------|
| Entity / Value Object | skillhub-domain | `com.iflytek.skillhub.domain.{submodule}/` |
| Repository Interface | skillhub-domain | `com.iflytek.skillhub.domain.{submodule}/` |
| Domain Service | skillhub-domain | `com.iflytek.skillhub.domain.{submodule}/service/` |
| Domain Event | skillhub-domain | `com.iflytek.skillhub.domain/event/` |
| Domain Exception | skillhub-domain | `com.iflytek.skillhub.domain/shared/exception/` |
| JPA Repository Impl | skillhub-infra | `com.iflytek.skillhub.infra.repository/` |
| Controller | skillhub-app | `com.iflytek.skillhub.controller/` |
| App Service | skillhub-app | `com.iflytek.skillhub.service/` |
| Query Repository | skillhub-app | `com.iflytek.skillhub.repository/` |
| DTO / Response | skillhub-app | `com.iflytek.skillhub.dto/` |
| OAuth2 / Auth Config | skillhub-auth | `com.iflytek.skillhub.auth/` |
| Search SPI / Impl | skillhub-search | `com.iflytek.skillhub.search/` |
| Storage SPI / Impl | skillhub-storage | `com.iflytek.skillhub.storage/` |
| Notification Service | skillhub-notification | `com.iflytek.skillhub.notification/` |

### Maven Modules

The parent POM (`server/pom.xml`) defines 7 modules with `spring-boot-starter-parent:3.2.3`:

```
skillhub-app | skillhub-domain | skillhub-auth | skillhub-search
skillhub-storage | skillhub-infra | skillhub-notification
```

### Repository vs Query Repository

- **Domain Repository** (`skillhub-domain`): Aggregate reads, state transitions, rule evaluation.
  Returns domain objects. Defined as interfaces, implemented in `skillhub-infra` via Spring Data JPA.
- **Query Repository** (`com.iflytek.skillhub.repository`): Read-model assembly, joins multiple
  sources, presentation projection. Returns DTOs. Implemented directly in `skillhub-app`.

Current query repositories:
- `GovernanceQueryRepository` / `JpaGovernanceQueryRepository`
- `MySkillQueryRepository` / `JpaMySkillQueryRepository`
- `ProfileReviewQueryRepository` / `JpaProfileReviewQueryRepository`
- `AdminSkillReportQueryRepository` / `JpaAdminSkillReportQueryRepository`

When a new read use case arrives:
1. If it's for state transition or domain rule → domain repository port
2. If it's for page/list/detail response assembly with joins → app query repository
3. If it's a thin single-aggregate read → direct domain repository call from app service
4. If direct SQL/EntityManager is needed → add class-level comment explaining why

### Building Backend Tests

Never run `./mvnw -pl skillhub-app clean test` directly under `server/`. Use:
```bash
make test-backend-app   # skillhub-app + dependencies (includes -am)
make test-backend       # all backend modules
```

Running clean test on skillhub-app alone can fall back to stale artifacts from the local Maven
repository, surfacing misleading `cannot find symbol` and signature-mismatch errors.

### User Identity Type

User identity is **always String** throughout the codebase. This covers:
- Authentication, API params, permissions, audit
- Resource owner, creator, reviewer, actor, submittedBy
- All user-associated fields

The `UserAccount` entity uses `@Column(length = 128)` for its ID. The platform needs to support
external SSO/OIDC/SCIM identity sources whose UIDs are typically stable strings.
