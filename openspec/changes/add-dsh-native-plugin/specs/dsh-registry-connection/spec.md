## ADDED Requirements

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
