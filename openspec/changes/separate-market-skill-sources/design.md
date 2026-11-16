## Context
The PostgreSQL search adapter already joins the current skill and derives count SQL from result predicates. Current provenance is available as skill.github_sync_managed. Portal search has no source parameter; CLI/compat callers share application search methods. See proposal.md for the problem.

## Goals / Non-Goals
Keep public discovery ACLs unchanged. Add source selection without schema/index changes or a new service. Do not deduplicate same-name skills or rebuild curated recommendation composition.

## Decisions
- Add a search source enum with backward-compatible SearchQuery/application overloads defaulting to ALL. Add an optional portal query parameter with explicit validation. Existing CLI/compat controllers continue using unchanged all-source methods.
- Filter on the joined current skill flag. A namespace-name filter would miss team-bound synchronization; adding provenance to the asynchronous index would add migration/rebuild and stale-index risks without benefit here.
- Add optional source projection to portal summaries. Preserve it through label/suite enrichment. Reuse namespace badges and a compact source control in the existing market toolbar.
- Explicit namespace URLs default to all sources, while unscoped market URLs default to platform. Source participates in query keys and navigation. Favorites keep their existing separate query and hide the inapplicable source control, matching existing label-filter behavior.
- Automatic homepage queries opt into platform; curated reads remain unchanged.

## Risks / Trade-offs
- Shared DTO and overload changes → keep existing constructors/methods and verify generated API plus existing consumers.
- Stale prior-source results during transitions → ensure source-aware cache identity and avoid presenting placeholder results as the new source.
- Query correctness → test real PostgreSQL paging/count and ACL boundaries as well as SQL unit checks.
- Existing source labels absent outside portal discovery → leave optional field absent instead of inventing provenance.

## Migration Plan
No database or index migration. Deploy backend support before the updated frontend. API/CLI omitted-source behavior stays ALL. Rollback returns previous market presentation without changing business data.
