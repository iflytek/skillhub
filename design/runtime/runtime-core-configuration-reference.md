# Runtime Core Configuration Reference

## Overview

This document consolidates the runtime configuration switches that directly determine how SkillHub starts and behaves.

It focuses on the core provider axes and the small set of properties that most often decide whether a local or delivered runtime is actually correct:

- relational database
- runtime state storage
- search provider
- local filesystem paths
- UASS local verification behavior

As of 2026-05-04, the current standard runtime direction is:

- database: `MySQL 8`
- runtime state: `Redis`
- search provider: `local-file-index`

For local troubleshooting and migration fallback, the following variants are still intentionally supported in current source startup docs:

- `MySQL 8 + memory + mysql-like`
- `MySQL 8 + memory + local-file-index`

## Test Boundary

- 单元测试、`DataJpaTest` 切片测试、以及用于 JaCoCo 行覆盖率门禁的测试，只要涉及 MySQL 持久化路径，都只能使用 `H2` 或 mock。
- 这类测试不得直连真实 MySQL，也不得依赖 `MySQLContainer`。
- 默认 surefire 单测 lane 必须在没有 MySQL 可用的环境里也能稳定运行，因此不得把真实 MySQL 作为前置依赖。
- 真实 MySQL 只保留给显式的 `*IntegrationTest`、`*RuntimeIntegrationTest`、迁移验证和回归验证；这类测试应明确打上独立标记并放入单独执行通道。
- 覆盖率门禁不能依赖本地或远程 MySQL 的可用性。

## Configuration Priority

Runtime behavior is determined in this order:

1. command-line or exported environment variables
2. selected Spring profile file such as `application-local-mysql.yml`
3. shared defaults in `application.yml`

Do not assume the profile file alone is authoritative. Several runtime combinations depend on environment overrides layered on top of the profile defaults.

## Provider Axes

### 1. Database

Primary properties:

| Property | Meaning | Typical values |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Selects the profile bundle | `local-mysql`, `local` |
| `LOCAL_MYSQL_DATASOURCE_URL` | MySQL JDBC URL for `local-mysql` | `jdbc:mysql://localhost:3306/skillhub?...` |
| `LOCAL_MYSQL_DATASOURCE_USERNAME` | MySQL username | `skillhub` |
| `LOCAL_MYSQL_DATASOURCE_PASSWORD` | MySQL password | `skillhub_dev` |

Rules:

- `local-mysql` means the authoritative runtime data lives in MySQL and schema is initialized by Flyway from `sql/migration-mysql`.
- Current formal source startup documentation only retains `local-mysql` as the repository-owned profile path.

### 2. Runtime State

Primary property:

| Property | Meaning | Values |
|---|---|---|
| `SKILLHUB_RUNTIME_STATE_PROVIDER` | High-level runtime state switch | `redis`, `memory` |

Derived effects:

| Derived property | `redis` | `memory` |
|---|---|---|
| `spring.session.store-type` | `redis` | `none` |
| `skillhub.ratelimit.mode` | `redis` | `memory` |
| `skillhub.auth.failure-throttle.mode` | `redis` | `memory` |
| `skillhub.auth.uass.cache-mode` | `redis` | `local` |

Rules:

- Treat `SKILLHUB_RUNTIME_STATE_PROVIDER` as the source of truth.
- Do not manually mix `memory` runtime state with `spring.session.store-type=redis` or similar leaf overrides.
- If you want a local no-Redis runtime, set `SKILLHUB_RUNTIME_STATE_PROVIDER=memory` instead of trying to disable Redis beans one by one.

### 3. Search Provider

Primary properties:

| Property | Meaning | Values |
|---|---|---|
| `skillhub.search.engine` | Search runtime family | `mysql` |
| `SKILLHUB_SEARCH_PROVIDER` | Active search backend | `local-file-index`, `mysql-like` |
| `skillhub.search.rebuild-on-startup` | Force startup rebuild for local-file-index | `true`, `false` |

Rules:

- `mysql-like` uses MySQL as the read path and `skill_search_document` as the authoritative search document snapshot.
- `local-file-index` uses embedded Lucene as the query backend.
- `local-file-index` depends on an initial synchronization step from MySQL authority data into the local Lucene directory.
- When provider is `local-file-index`, `Query`, `Index`, and `Rebuild` beans must all switch together.

### 4. Local Search Index Path

Primary property:

| Property | Meaning | Typical value |
|---|---|---|
| `SKILLHUB_SEARCH_LOCAL_FILE_INDEX_DIRECTORY` | Local Lucene index directory | `${user.home}/.skillhub/local-mysql/search-index` |

Rules:

- This path must be writable by the current process.
- Deleting the directory is safe if you intend to trigger a full rebuild.
- If the directory is missing or uninitialized, the startup synchronizer now triggers `rebuildAll()` automatically when `local-file-index` is active.
- If the directory is corrupted and Lucene cannot open it, startup also treats it as a rebuild case.

### 5. Local Storage Path

Primary properties:

| Property | Meaning | Typical value |
|---|---|---|
| `LOCAL_MYSQL_STORAGE_BASE_PATH` | Local package/object storage base path under MySQL runtime | `${user.home}/.skillhub/local-mysql/storage` |

Rules:

- This path is independent from the Lucene index directory.
- Do not place Lucene index files under the package storage directory.

### 6. UASS Local Verification

Primary properties:

| Property | Meaning | Typical value |
|---|---|---|
| `SKILLHUB_AUTH_UASS_ENABLED` | Enables UASS browser-login flow | `true`, `false` |
| `SKILLHUB_AUTH_UASS_MOCK_LOGIN_BASE_URL` | External mock third-party login page origin | `http://localhost:3001` |
| `skillhub.auth.uass.base-url` | Upstream gateway base | `mock://self` for local verification |

Rules:

- If `SKILLHUB_AUTH_UASS_ENABLED=false`, the login page will not show the enterprise entry.
- To preserve the local third-party redirect simulation, use:
  - `SKILLHUB_AUTH_UASS_ENABLED=true`
  - `SKILLHUB_AUTH_UASS_MOCK_LOGIN_BASE_URL=http://localhost:3001`
- For local mock verification, keep `base-url: mock://self`.

### 7. UASS Bootstrap Admins

Primary config section:

```yaml
skillhub:
  auth:
    uass:
      admin-users:
        - ussId: uass-admin-003
```

Rules:

- The listed `ussId` values are not customizable role bundles anymore.
- A listed user is treated as a bootstrap full-platform administrator.
- On first account creation through UASS, the runtime grants `SUPER_ADMIN`.
- This is applied only at first user creation, not retroactively to an already existing account.

## Recommended Runtime Combinations

### A. Standard local MySQL with local Lucene and memory runtime state

Use when:

- you want MySQL as authority
- you want `local-file-index`
- you do not want Redis involved

Required settings:

```bash
SPRING_PROFILES_ACTIVE=local-mysql
SKILLHUB_SEARCH_PROVIDER=local-file-index
SKILLHUB_RUNTIME_STATE_PROVIDER=memory
```

Optional local UASS mock:

```bash
SKILLHUB_AUTH_UASS_ENABLED=true
SKILLHUB_AUTH_UASS_MOCK_LOGIN_BASE_URL=http://localhost:3001
```

### B. MySQL fallback search path

Use when:

- Lucene is unavailable
- you need to compare `mysql-like` behavior

Required settings:

```bash
SPRING_PROFILES_ACTIVE=local-mysql
SKILLHUB_SEARCH_PROVIDER=mysql-like
SKILLHUB_RUNTIME_STATE_PROVIDER=memory
```

## Startup Synchronization Rules For `local-file-index`

Current behavior:

- if provider is `local-file-index` and the Lucene directory is missing, startup logs a warning and triggers `rebuildAll()`
- if provider is `local-file-index` and the Lucene directory exists but is not an initialized index, startup logs a warning and triggers `rebuildAll()`
- if provider is `local-file-index` and Lucene cannot open the index, startup treats it as corruption, logs a warning, and triggers `rebuildAll()`
- if `skillhub.search.rebuild-on-startup=true`, startup rebuilds even when the index is already healthy

This mechanism exists to prevent the silent failure mode where:

- MySQL contains the authoritative published skills
- `skill_search_document` is correct
- but Lucene is empty or broken
- and the UI search returns zero results

## Common Failure Patterns

### 1. Skill exists in database but search returns nothing

Check:

1. is provider `local-file-index` or `mysql-like`
2. if `local-file-index`, does the Lucene directory exist and contain a valid index
3. if not, restart with startup sync or call the rebuild endpoint

### 2. UASS button missing on login page

Check:

1. `SKILLHUB_AUTH_UASS_ENABLED=true`
2. `/api/v1/auth/methods` includes `UASS_REDIRECT`
3. `mock-login-base-url` points to the `3001` frontend if you expect the mock third-party page

### 3. Runtime unexpectedly talks to the wrong database or Redis

Check:

1. whether the jar was rebuilt after profile file changes
2. whether the intended profile file is actually packaged into the jar
3. whether `SKILLHUB_RUNTIME_STATE_PROVIDER` is still `redis`

## Suggested Operator Checklist

Before saying a runtime is correctly configured, verify:

1. `SPRING_PROFILES_ACTIVE` is the expected profile
2. the database endpoint in logs matches the intended backend
3. `/actuator/health` is `UP`
4. `/api/v1/auth/methods` reflects the expected UASS visibility
5. the search provider matches the intended backend
6. for `local-file-index`, the Lucene directory exists and has been initialized
