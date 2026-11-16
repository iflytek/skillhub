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
The market SHALL default to platform when no source or explicit namespace is selected. Users SHALL be able to select all or github, with selection retained in the URL and query identity and pagination reset when source changes. Explicit namespace links without source SHALL preserve their existing scoped results. Favorites SHALL preserve their existing all-source behavior. The interface SHALL distinguish authoritative synchronization provenance while retaining namespace identity.

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
