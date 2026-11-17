# Proposal

## Why

Issue #1095 reports GitHub-synchronized packages overwhelming the default market. Distinguish platform content from GitHub synchronization without changing existing API/CLI defaults, visibility or package coordinates.

## What Changes

- Add an optional ALL / PLATFORM / GITHUB search source, evaluated from the current Skill's GitHub-managed flag before ranking, count and pagination.
- The market defaults to PLATFORM and provides compact source selection; explicit namespace links preserve scoped discovery. Favorites retain their existing all-source behavior.
- Return authoritative source information for market cards. `/github` explicitly selects GitHub source while retaining its catalog scope.
- Apply PLATFORM to automatic homepage popular/latest lists; preserve explicitly curated configuration.
- Keep omitted API source and CLI/compat search behavior at ALL. No data migration, deduplication or access-policy change.

## Capabilities

### New Capabilities
- `skill-search-source`: Compatible source filtering and source-aware market discovery.

### Modified Capabilities
None.

## Impact

Search SPI/PostgreSQL adapter, portal application/controller/summary, React market/router/query keys/cards, automatic homepage queries, generated OpenAPI and bilingual docs. No new production dependency, service or schema migration.
