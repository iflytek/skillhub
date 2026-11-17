## ADDED Requirements

### Requirement: REQ-SOURCE-01 Compatible source filtering
Search SHALL accept an optional source selection of all, platform or github. Omission SHALL preserve all-source API behavior. Source filtering SHALL use authoritative synchronization provenance, not a skill name prefix, and SHALL precede ranking, pagination and total counting. It SHALL NOT relax visibility, hidden/status or installation checks.

#### Scenario: Existing clients omit source
- **WHEN** an existing API or CLI client searches without a source
- **THEN** matching authorized platform and GitHub skills remain eligible as before

#### Scenario: Paged platform discovery
- **WHEN** a user requests platform source with a page size smaller than the matching set
- **THEN** results and total contain only authorized non-GitHub-managed skills, without holes from client-side removal

#### Scenario: GitHub synchronization inside a team
- **WHEN** a GitHub-managed skill belongs to a team namespace
- **THEN** it belongs to github source and retains the team's visibility restrictions

#### Scenario: Invalid source
- **WHEN** the portal API receives an unsupported source
- **THEN** it rejects the parameter instead of silently broadening the query

### Requirement: REQ-SOURCE-02 Source-aware market navigation
The market SHALL default to platform when no source or explicit namespace is selected. Users SHALL be able to select all or github, with selection retained in the URL and query identity and the accumulated list reset when source changes. Explicit namespace links without source SHALL preserve their existing scoped results. Favorites SHALL preserve their existing all-source behavior. The interface SHALL distinguish authoritative synchronization provenance while retaining namespace identity.

#### Scenario: Restore a filtered market
- **WHEN** a user refreshes or returns from a skill detail
- **THEN** the source, keyword, namespace and sorting selection are restored

#### Scenario: Explicit catalog namespace
- **WHEN** an existing link searches namespace github without an explicit source
- **THEN** catalog matches are still discoverable

#### Scenario: Favorite discovery
- **WHEN** a user enables favorites
- **THEN** their existing favorite search is not silently narrowed by the market's default source

### Requirement: REQ-SOURCE-03 Homepage and catalog boundaries
Automatic homepage popular/latest skill lists SHALL explicitly request platform source. Explicitly curated recommendations and topics SHALL keep configured members. The GitHub page SHALL continue its existing catalog scope. No skill SHALL be deduplicated, hidden, republished or made inaccessible merely because of its source.

#### Scenario: Curated GitHub skill
- **WHEN** an administrator explicitly selects a GitHub-managed skill in a curated recommendation
- **THEN** the configured recommendation is retained


### Requirement: REQ-SOURCE-04 Market card information hierarchy
Market cards SHALL retain the original title and namespace-pill header, summary, version pill and download/bookmark statistics. The bottom row SHALL show the existing update time on the left and authoritative source on the right. This row SHALL be separated from statistics by the original subtle divider. Missing source SHALL NOT be inferred. Other card consumers SHALL retain their existing layout unless they opt into the market variant.

#### Scenario: Narrow card with long metadata
- **WHEN** a market card has a long namespace, label or version, or large counters
- **THEN** metadata remains within the card, names remain available in full text/title, and statistics can wrap while the source stays in the separate bottom row

#### Scenario: Existing information and navigation
- **WHEN** a market card has Suite membership, compliance or rating information
- **THEN** these remain visible and clicking the card or pressing Enter/Space retains the existing skill-detail navigation


### Requirement: REQ-SOURCE-05 Unified market filter panel
The market SHALL group its enlarged search input, compact source selection and independent label row into a single panel. Result count and the sort selector SHALL appear below that panel, with a current-scope hint on desktop. Source selection SHALL use icon-plus-text controls; favorites SHALL retain their existing all-source behavior and login boundary.

#### Scenario: Desktop label selection
- **WHEN** a user opens more labels and searches for an existing label
- **THEN** selecting it applies the existing URL filter, resets the page, closes the bounded popover and restores focus

#### Scenario: Mobile label selection
- **WHEN** a user opens all labels on a narrow viewport
- **THEN** a bounded bottom sheet provides a temporary selection, Cancel discards it, Apply commits it and closes the sheet, and applying the unchanged selection does not toggle it off

#### Scenario: Clear an empty filtered result
- **WHEN** a query, namespace or label produces no results and the user clears search and labels
- **THEN** those conditions reset together while source, sort and favorites state remain unchanged


### Requirement: REQ-SOURCE-06 Incremental market list and return context
The market SHALL replace numbered pagination with near-bottom incremental loading using the existing paged API. It SHALL start at page zero, deduplicate displayed skill ids, coalesce concurrent append requests, and stop on an empty batch or the reported total. Filtering SHALL retain URL semantics and reset the destination accumulation to its first batch. Existing API/CLI pagination SHALL remain unchanged; legacy market page links SHALL retain their filters and start from the first batch.

#### Scenario: Append fails
- **WHEN** a later batch fails to load
- **THEN** loaded cards stay visible, automatic retries stop, and an explicit retry requests the failed batch without restarting the list

#### Scenario: Reach the end
- **WHEN** all available batches have loaded
- **THEN** the market stops requesting more and displays a final count, with no numbered pagination

#### Scenario: Return from skill detail
- **WHEN** a user opens a skill and returns while the list remains in the current session cache
- **THEN** loaded batches, filters and scroll position are restored without jumping to page one; stale cached data may refresh under the normal freshness policy

#### Scenario: Scope changes while loading
- **WHEN** the user changes filters, language or identity before an old request resolves
- **THEN** the late response cannot populate the new scope and the new list starts with its own first batch

#### Scenario: Restrained market header
- **WHEN** the market renders
- **THEN** it starts with the search panel rather than a duplicate market title and description; the desktop search uses a 64px field (56px on mobile) and favorites use a pressed-state bookmark capsule, and the compact sort menu matches its trigger width with separated checkmark and text

#### Scenario: No empty desktop label entry
- **WHEN** six or fewer labels are available
- **THEN** all labels appear as desktop shortcuts and More labels is hidden; the mobile all-label sheet remains available because mobile shortcuts are limited
- **WHEN** more than six labels are available
- **THEN** More labels offers only the additional labels by default and searches the full catalog when a query is entered
