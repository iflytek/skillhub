## ADDED Requirements

### Requirement: Official default with explicit source control
The plugin SHALL default an omitted registryUrl to https://skill.xfyun.cn using its Host configuration schema. An explicitly empty or whitespace-only value SHALL disconnect without fallback. Existing custom addresses SHALL remain selected. The plugin SHALL NOT inherit CLI source configuration or credentials, and initialization or reading source configuration alone SHALL NOT initiate Registry requests.

#### Scenario: First installation or empty configuration object
- **WHEN** the plugin starts without configuration or with an empty configuration object
- **THEN** the selected Registry is https://skill.xfyun.cn and requests begin only through existing explicit read or user interface flows

#### Scenario: Explicitly disconnected installation
- **WHEN** a new or existing configuration contains an empty or whitespace-only registryUrl
- **THEN** the source remains unconfigured and search is refused without contacting the official default

#### Scenario: Existing custom source
- **WHEN** an existing configuration contains a custom Registry address
- **THEN** that source is preserved through upgrade with existing URL normalization and source-isolation behavior

### Requirement: One anonymous Registry source
The plugin SHALL connect to one configured SkillHub Registry using anonymous requests and SHALL NOT implement login, Token configuration, or credential reuse in this release.

#### Scenario: Open public source
- **WHEN** the configured source supports anonymous listing and downloads
- **THEN** the user can browse skills without an account or a separately installed SkillHub CLI

#### Scenario: Authentication required
- **WHEN** the source or a selected skill requires authentication
- **THEN** the plugin explains that anonymous access is unavailable without attempting login or bypassing the restriction

### Requirement: Source changes invalidate stale operations
The plugin SHALL bind requests and installation plans to their source and SHALL invalidate stale results when the configured source changes.

#### Scenario: Change source while viewing details
- **WHEN** the user changes the Registry address
- **THEN** old details and unsubmitted installation plans cannot be submitted against the new source

#### Scenario: Failed connection
- **WHEN** a source request fails
- **THEN** the plugin shows an actionable error and retry without displaying cached results as current

### Requirement: Shared open-source and SaaS API contract
The plugin SHALL consume the same public Registry HTTP contract for open-source and SaaS instances. Public server API definitions and contract tests SHALL be maintained in the open-source repository and applied to each supported SaaS implementation. Plugin Remote methods and local filesystem operations SHALL remain within the DSH plugin rather than becoming SaaS installation endpoints.

#### Scenario: Equivalent supported instances
- **WHEN** the same plugin queries open-source and SaaS instances that implement the public contract
- **THEN** coordinates, fixed-version resolution, JSON envelopes, raw file responses and errors have consistent semantics without product-name-specific branches

#### Scenario: SaaS synchronization is deferred
- **WHEN** SaaS has not implemented a required Registry endpoint
- **THEN** that combination is not declared supported, while a separately verified open-source release can be delivered

#### Scenario: Optional module is not available
- **WHEN** versions, reviews or audit information is unavailable on an instance
- **THEN** the plugin applies the configured auto/show/hide behavior without disabling otherwise valid core installation or bypassing access policies

### Requirement: Backward-compatible Registry evolution
Registry API changes SHALL preserve supported clients or provide an explicit versioned migration. Server changes required by a new plugin version SHALL be deployed before that plugin is declared compatible with the target instance. Contract validation SHALL cover JSON responses, raw content, download redirects and fingerprint consistency.

#### Scenario: Additive API change
- **WHEN** an optional response field is introduced
- **THEN** supported older plugins continue to parse and use the required fields, and the contract tests cover both response forms

#### Scenario: Breaking API change
- **WHEN** a required field, type, access rule or version-resolution meaning changes incompatibly
- **THEN** a versioned contract or documented migration period is defined before the change replaces a supported contract

#### Scenario: Temporary failure is not capability absence
- **WHEN** an optional request fails due to network errors or server failure
- **THEN** the plugin retains error and retry behavior without permanently declaring the capability unsupported or presenting an empty successful response

### Requirement: Test a draft source without saving
The plugin SHALL provide an explicit connection test for the draft Registry address. The Host SHALL validate the address and anonymously read the public catalog, using the supported basic-search fallback when necessary, within a ten-second total deadline. Testing SHALL NOT change the active source, source revision, configuration, or installed skills.

#### Scenario: Draft source is reachable
- **WHEN** the user tests a valid draft address and the public read succeeds
- **THEN** the configuration page reports the available catalog or basic-search capability and elapsed time without claiming the address was saved or installation was verified

#### Scenario: Draft changes during a test
- **WHEN** the address changes, the user resets edits, or the configuration page unmounts during a test
- **THEN** the pending request is cancelled and its result cannot overwrite the current draft's state

#### Scenario: Invalid or unavailable source
- **WHEN** the address is invalid, public access is denied, the response is incompatible, or the deadline expires
- **THEN** the page shows an appropriate safe error without exposing response contents, credentials, or a false successful connection


### Requirement: Downloadable version pagination
The open-source versions endpoint SHALL accept optional `downloadableOnly`, defaulting to false. When true it SHALL preserve skill authorization, filter by existing installability before pagination, sort by publication time descending with nulls last and deterministic creation-time/id descending tie breakers, and return the filtered total. Default manager history SHALL preserve lifecycle grouping. Public version ordering SHALL be newest first.

#### Scenario: Filtered pages remain globally ordered
- **WHEN** published versions include unavailable downloads and publication order differs from repository creation order
- **THEN** the client receives only installable lifecycle versions, globally ordered before page slicing with the filtered total
- **AND** object-storage presence remains checked by the existing resolution/download flow

#### Scenario: Existing consumers omit the new parameter
- **WHEN** a management consumer omits downloadableOnly
- **THEN** its authorized lifecycle history remains available and no authentication policy changes

### Requirement: Saved sources with one shared active source
The plugin SHALL support named saved sources with one active registryUrl shared by a Profile/plugin instance. Configuration writes SHALL preserve revision CAS, omitted defaults and explicit disconnection. Removing a source SHALL NOT remove its installations.

#### Scenario: Shared switch and conflicting edits
- **WHEN** one page switches from A to B while another holds an old configuration revision
- **THEN** all pages observe B and stale writes are rejected rather than overwriting the selection

### Requirement: Local management independent of the active market
The plugin SHALL retain trusted managed installations from other sources, including while disconnected. Local actions SHALL use their original inventory identity and preserve existing filesystem and confirmation protections. Different sources with equal coordinates SHALL NOT merge.

#### Scenario: Old source installation after a switch
- **WHEN** A has an installation and the active source becomes B or disconnected
- **THEN** the installation remains locally usable and manageable and its detail does not request B's same-name skill

### Requirement: Update checks restricted to the active source
The Host SHALL restrict individual and bulk update checks and upgrade preparation to installations from the active source before remote requests. Other-source installations SHALL NOT count as latest, failed or updateable. Switching SHALL discard old results and invalidate unsubmitted plans without abandoning accepted transactions.

#### Scenario: Slow response after switching
- **WHEN** A's update check completes after switching to B
- **THEN** its result is discarded, only B participates in current update counts, and switching back to A requires a fresh check before upgrade

#### Scenario: Cross-source upgrade attempt
- **WHEN** an old-source item is submitted for an update check or upgrade against the active source
- **THEN** the Host rejects the request before resolve and does not replace its installation with a same-name package

#### Scenario: Model checks updates after a source switch
- **WHEN** a user asks the conversation agent to check installed skills for updates
- **THEN** the tool contract directs the agent to obtain fresh opaque routing references from the installed list, check only current-source items, and distinguish update eligibility from an available newer version
- **AND** a stale context is not presented as missing human approval; the agent is directed to refresh once and report an unresolved failure rather than repeatedly retry or invent a confirmation step

### Requirement: Settings grouped without losing unsaved changes
The settings page SHALL group source configuration and detail visibility into keyboard-accessible tabs. Switching tabs SHALL preserve the same unsaved draft and SHALL NOT save or test a connection. Each tab SHALL use one content card. Source selection and management SHALL share a single list with inline editing on demand, without a duplicated current-address editor. A current URL absent from saved sources SHALL remain visible and manageable.

#### Scenario: Save changes from multiple settings tabs
- **WHEN** a user edits the source and detail visibility in separate tabs and saves
- **THEN** all pending edits are submitted together with their original configuration revision
- **AND** an invalid hidden source field brings the source tab and management area into view; conflicting external edits are rejected without silently rebasing the draft

#### Scenario: Return to the saved values
- **WHEN** edits return to the saved values without an intervening configuration revision change
- **THEN** no unsaved-change state remains and save is disabled
- **AND** returning values after an external revision change does not bypass conflict detection
