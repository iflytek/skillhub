# MySQL Runtime And Search Provider Migration

## Overview

This document captures durable rules from the completed migration work that moved SkillHub away from PostgreSQL as the default runtime path.

As of 2026-05-04, the current standard runtime is:

- relational database: `MySQL 8`
- runtime state: `Redis`
- search provider: `local-file-index`

`mysql-like` remains as an explicit search fallback.
Historical materials may still mention `local-h2` and `h2-like`, but they are no longer part of the current formal runtime path.

## Status Checkpoint

As of 2026-05-05, the "formal runtime exit" part of the H2 cleanup is mostly complete, but the repository is not yet fully free of H2-related residuals.

### Completed

- `local-h2` is no longer a current formal source runtime entrypoint.
- current-entry docs no longer describe `local-h2` / `h2-like` as the standard runtime path.
- the old `h2-like` search implementation is no longer part of the current source tree.
- the main runtime and migration direction has been converged to `MySQL 8 + Redis + local-file-index`.

### Still Residual

- some code paths still mention `local-h2` as a compatibility or historical profile, for example local dev data seeding.
- some SQL bootstrap materials for `local-h2` still remain in the source tree as historical or compatibility leftovers.
- some search bean wiring still uses `havingValue = "h2"` conditions even though the old H2 query-provider path has already been removed.
- H2 still exists in parts of the Maven build and in at least one focused repository test.
- some project guidance and coverage inventory files still contain stale H2-era references.

### Practical Interpretation

Use the following boundary until the remaining cleanup work is finished:

- "H2 removed from the formal runtime path": yes
- "H2 fully removed from source, tests, dependency graph, and project guidance": not yet

Do not update design or delivery notes to claim full H2 removal until the residual items above are cleared.

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

Do not keep PostgreSQL-only SQL in Spring Data repository annotations if the runtime must support MySQL.

Preferred pattern for simple counter or stats upsert:

1. `UPDATE`
2. conditional `INSERT`
3. retry `UPDATE`

This keeps repository interfaces portable and moves dialect handling to an adapter boundary.

### 7. Redis-backed app state needs its own bean boundary

If app runtime state uses Redis directly, define a dedicated bean name such as `skillhubRedisTemplate` and inject it via `@Qualifier`.

Do not rely on the default Spring `redisTemplate` bean for app-specific state wiring. The boundary becomes ambiguous in both tests and production bootstrapping.

### 8. Dialect validation must use the real target database

Keep H2 for fast tests, but do not use H2 to prove MySQL-specific behavior.

Use targeted MySQL validation for:

- schema bootstrapping and Flyway migration
- JSON/text mapping compatibility
- dialect-sensitive write paths
- search provider query behavior

Prefer Testcontainers MySQL plus the real MySQL migration directory, with `ddl-auto=none`.

Current residual note:

- this rule already reflects the intended target state
- however, the repository still contains limited H2-based helper testing and compatibility remnants
- those remnants should be treated as transitional, not as evidence that H2 is still a valid formal runtime path

### 9. Migration directories should follow runtime reality

If one runtime needs a different relational dialect, give it its own Flyway migration directory and validate empty-database startup against that directory.

Do not force one migration tree to serve incompatible dialect assumptions.

### 10. Runtime verification must prove the selected path, not only startup

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

## What "PostgreSQL Removed" Means

For SkillHub, PostgreSQL removal means:

- it is no longer the default runtime database
- it is no longer the standard compose dependency for local, staging, or release entrypoints
- PostgreSQL-only search runtime beans are no longer part of the current default code path
- current-entry docs no longer describe PostgreSQL as the standard path

It does not require erasing all historical references.
Historical notes, reset scripts, and archive materials may remain if they are clearly not part of the current runtime entry path.
