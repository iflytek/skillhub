# MySQL Runtime And Search Provider Migration

## Overview

This document captures durable rules from the completed runtime migration that converged SkillHub on the current MySQL search path.

As of 2026-05-04, the current standard runtime is:

- relational database: `MySQL 8`
- runtime state: `Redis`
- search provider: `local-file-index`

`mysql-like` remains as an explicit search fallback.

Current repository profile mapping:

- `dev`: `MySQL + memory + mysql-like`
- `test`: `MySQL + Redis + mysql-like`
- `prod`: `MySQL + Redis + local-file-index`
- `qa`: `H2 + memory + local-file-index` for automated tests only

## Runtime Axes

Treat relational database, runtime state, and search as three separate provider axes.

- relational database: `mysql8`
- runtime state: `memory`, `redis`, `disabled`
- search provider: `mysql-like`, `local-file-index`

Do not bundle these switches into one migration step. The database move, Redis adoption, and search backend replacement must stay independently switchable.

## Durable Design Rules

### 1. Keep provider selection centralized

If a capability needs to switch as one runtime bundle, expose one top-level provider key and derive leaf properties from it.

Use this pattern for runtime state:

- one source of truth: `skillhub.runtime.state.provider`
- derived modes: session, rate limit, auth throttle, UASS state

Do not keep old per-profile leaf overrides after the provider default is unified. They drift quickly and produce mixed runtime behavior.

### 2. Search provider wiring must switch as a set

Search runtime selection is not only a `SearchQueryService` problem.
`Query`, `Index`, and `Rebuild` beans must all follow the same provider condition.

If only query switches, startup can still fail in listeners and app services that depend on index or rebuild beans.

### 3. Keep the search contract narrow

The stable provider contract is:

- `skillIds`
- pagination metadata

Hydration stays in the application layer.
Do not let a provider return provider-specific aggregate payloads just to avoid later joins.

### 4. For local-file-index, document fields must be explicit

Any new filter or sort capability must update all three parts together:

- `SkillSearchDocument`
- index writer
- rebuild source

Do not only update the query service. If the field is not in the document model, the feature is not actually implemented.

### 5. Local-file-index write semantics should stay idempotent

Use `skillId` as the Lucene term key.

- upsert: `updateDocument`
- delete: `deleteDocuments`
- full rebuild: clear the whole index directory, then rebuild from authoritative data
- scoped rebuild: keep unrelated documents

Create the index directory before the first write. Empty-path first writes otherwise fail.

### 6. Cross-dialect writes should be isolated behind adapters

Do not keep database-specific SQL in Spring Data repository annotations if the runtime must support MySQL.

Preferred pattern for simple counter or stats upsert:

1. `UPDATE`
2. conditional `INSERT`
3. retry `UPDATE`

This keeps repository interfaces portable and moves dialect handling to an adapter boundary.

### 7. Redis-backed app state needs its own bean boundary

If app runtime state uses Redis directly, define a dedicated bean name such as `skillhubRedisTemplate` and inject it via `@Qualifier`.

Do not rely on the default Spring `redisTemplate` bean for app-specific state wiring. The boundary becomes ambiguous in both tests and production bootstrapping.

### 8. Unit tests that touch MySQL-backed code must stay off real MySQL

Unit tests, repository slice tests, and JaCoCo coverage-gate tests that exercise MySQL-backed code must use `H2` or mocks.

Do not require a real MySQL server or `MySQLContainer` for unit coverage work.

The default unit-test lane must remain runnable in CI environments that do not provide a database endpoint.
If a test depends on a real MySQL instance, it is not a unit-test-lane test anymore.

Real MySQL is reserved for explicit integration, runtime, or regression validation such as:

- Flyway migration boot verification
- dialect-sensitive runtime smoke
- end-to-end search, approval, promotion, or delete flows intentionally marked as integration/runtime tests

These MySQL-backed tests should be isolated with an explicit integration marker or profile so they do not silently slip back into the default coverage lane.

If an existing test currently sits in the unit-test lane but requires `MySQLContainer`, it must be handled in one of two ways:

- rewrite it as H2-backed or mock-backed
- re-scope and rename it as integration/runtime validation, then remove it from the unit coverage plan

### 9. Dialect validation must use the real target database

Use targeted MySQL validation for:

- schema bootstrapping and Flyway migration
- JSON/text mapping compatibility
- dialect-sensitive write paths
- search provider query behavior

Prefer Testcontainers MySQL plus the real MySQL migration directory, with `ddl-auto=none`.

### 10. Migration directories should follow runtime reality

If one runtime needs a different relational dialect, give it its own Flyway migration directory and validate empty-database startup against that directory.

Do not force one migration tree to serve incompatible dialect assumptions.

### 11. Runtime verification must prove the selected path, not only startup

The minimum reliable verification stack is:

- configuration binding tests
- runtime selection tests
- focused MySQL persistence tests for dialect-sensitive code
- real `actuator/health` boot verification
- real search HTTP verification with seeded data
- browser verification for one protected flow and one search flow

When validating local browser flows, first confirm the frontend proxy points at the current backend instance. A stale Vite proxy can produce false frontend `500` results.

## Validation Playbook

### Binding and wiring

- use `ApplicationContextRunner` for provider selection tests
- assert both selected beans and the absence of wrong-provider beans
- keep profile binding tests alongside runtime profiles

### Persistence and migration

- keep unit-level persistence coverage tests on H2 or mocks
- validate empty-db boot with the real migration directory
- add focused MySQL tests for dialect-sensitive entities and repositories
- prefer small repository or entity tests over large bootstraps when possible

### Runtime smoke

- rebuild the current branch jar before runtime verification
- confirm the target port is owned by the current SkillHub process
- use a deterministic searchable fixture such as `mysql-runtime-fixture`

### Build discipline

- run Maven verification serially when modules share the same `target` path expectations
- do not trust stale JaCoCo module reports; regenerate them before coverage decisions

## What Runtime Convergence Means

For SkillHub, runtime convergence means:

- it is no longer the default runtime database
- it is no longer the standard compose dependency for local, staging, or release entrypoints
- search runtime beans switch only between the supported MySQL-backed providers
- current-entry docs describe only the active runtime path and its explicit fallback
