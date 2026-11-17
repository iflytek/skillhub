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


## Confirmed market-card refinement
Use an opt-in `market` variant of SkillCard to restore the user-selected original card composition: title at left, namespace pill at upper right, three-line summary, version pill and download/bookmark statistics in their existing row. Keep the update time on the bottom left and add a restrained icon-plus-text source on the bottom right, separated from statistics by a subtle divider. Market cards do not add a fallback avatar. Preserve Suite/compliance/rating information, optional explicit label props and keyboard navigation. Other card consumers retain their existing presentation. Do not infer missing provenance.

The larger search/filter composition and infinite-scroll states remain separately designed follow-ups; this refinement does not implement those query/navigation changes.


## Confirmed filter panel adoption
Keep the existing URL handlers and query hooks. Compose the controls in MarketSearchControls using the existing SearchBar market variant, Radix Popover for searchable desktop labels, Radix Dialog for a mobile draft-and-apply sheet, and the shared Select for sorting. All search/source/label/favorite/sort semantics remain in SearchPage; the new component owns only open states and temporary label selection. Close label overlays on breakpoint changes to avoid hidden focus traps. Search remains explicit, not triggered on each keystroke. Preserve applicable analytics identifiers while moving their hosts; retire numbered-pagination tracking and add explicit load-more/retry action markers for the incremental list.

Match the confirmed desktop panel: 24px inset, 64px search form, 40px source segment container with 32px options, separate 28px label controls; mobile uses a 16px inset and a bottom sheet. Keep title, label and control text from locale resources. A filtered empty state offers a combined search/label reset; a genuinely empty GitHub source offers platform discovery. The duplicate page title/description are removed. Desktop search uses a 36px action; mobile search is 56px with a 32px action and 16px inset. Favorites use a bookmark capsule (112×32px desktop, 84×28px mobile), with aria-pressed, a filled icon and theme accent when selected. Reserve the source row height when showing favorite scope so switching modes does not move the filter rows.


## Incremental loading and sort-menu detail
Use a market-specific useInfiniteQuery under the existing skills/search invalidation prefix, keyed by normalized filters, viewer and language. Keep existing non-market queries and paged endpoints unchanged. IntersectionObserver watches a sentinel 320px before the end; fetchNextPage uses cancelRefetch=false, and fetching/error/restore guards prevent duplicate or automatic failure loops. Existing results remain visible on append failure. A keyboard-accessible load-more action also works when IntersectionObserver is unavailable. Favorites keep the existing private all-source query and expose its filtered array in bounded increments.

Skill data remains in QueryClient. A bounded module-local map stores only scroll position and favorite display count, scoped to identity/language/filters. Opening detail saves the position; returning restores after cached cards have rendered, before enabling automatic append. Explicit filter actions clear the destination position and trim its cache to the first batch. Cached lists retain the normal 30-second stale policy; an expired cache or hard reload does not guarantee previous batch/scroll restoration. No data migration, global scroll-restoration setting or extra production dependency is introduced.

The market sort Select overrides only its own menu width/alignment and item padding: menu equals trigger width, right edges align, and the indicator has a separate gutter. Shared Select defaults for other pages remain unchanged.

## Compact additional-label picker
Desktop More labels defaults to labels after the six visible shortcuts; it does not repeat All. Typing searches the entire label catalog. The popover is 280px wide with a 32px search field and 32px truncated rows (full title on hover), a 240px scroll limit, and theme-accent selected background plus checkmark. Hide the desktop More labels entry when the catalog contains six or fewer labels; do not invite users into an empty picker. Searches with no matches display an empty-result hint. Selection closes the popover and restores trigger focus. The mobile all-label draft/apply sheet retains its full catalog and All choice.
