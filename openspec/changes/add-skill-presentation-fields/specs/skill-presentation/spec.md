## ADDED Requirements

### Requirement: Presentation metadata
The system MUST accept optional `metadata.short-description` and `metadata.overview` in SKILL.md. The short description MUST be at most 200 characters and the overview at most 20,000 characters.

#### Scenario: Validate optional package presentation fields
- **GIVEN** a package with optional short-description and overview metadata
- **WHEN** the package is validated
- **THEN** values within the respective limits are accepted and values beyond them are rejected

### Requirement: Web publish precedence
A web-provided value MUST override the package value for that submitted version. The archive MUST remain unchanged. Reset MUST remove the custom override so the package/default resolution applies.

#### Scenario: Override package defaults without rewriting files
- **GIVEN** package metadata and explicit web values
- **WHEN** the version is submitted
- **THEN** the explicit web edits win and the original package files remain unchanged

#### Scenario: Restore defaults for a later version
- **GIVEN** a reset request
- **WHEN** the version publishes
- **THEN** it uses package defaults and later default versions read their own package defaults

### Requirement: Lifecycle isolation
A pending version MUST NOT replace the currently published title, summary, or overview. Published metadata MUST be restored by rollback/yank using the selected published version snapshot.

#### Scenario: Keep a pending update separate
- **GIVEN** an existing published version
- **WHEN** an update is submitted for review
- **THEN** the public title, summary, and overview remain unchanged until publication

#### Scenario: Restore a published snapshot after rollback
- **GIVEN** published versions with different presentation fields
- **WHEN** yanking changes the published pointer
- **THEN** the public presentation follows the selected published version snapshot

### Requirement: Search and detail
The effective short description MUST be used as the public summary. The original package description MUST remain searchable. The detailed overview MUST NOT be indexed and MUST fall back to README.md then SKILL.md when no explicit/package overview exists.

#### Scenario: Resolve default and custom presentation
- **GIVEN** an accessible skill version
- **WHEN** a reader views its details or searches for it
- **THEN** the summary uses the effective short description, the original description remains searchable, and the overview is detail-only with the documented fallback order

### Requirement: Version-scoped detail navigation
The detail page MUST present Overview, Skill instructions, Files, and Versions tabs. Skill instructions MUST read the selected version's original root `SKILL.md`, support rendered and source modes, and distinguish loading, missing-file, and read-failure states. The overview MUST NOT contain an extra View SKILL.md button.

#### Scenario: Browse an accessible historical version
- **GIVEN** multiple accessible versions
- **WHEN** a reader chooses View this version in the Versions tab
- **THEN** the page returns to Overview and updates the URL version without losing other search context
- **AND** the summary, overview, instructions, file inventory, and install/download targets follow that same version
- **AND** a historical-version notice and return-to-latest-published action are available where applicable
- **AND** browsing does not publish, rerelease, or submit a review

#### Scenario: Restore a version from browser history
- **GIVEN** a URL selecting an accessible version
- **WHEN** the reader refreshes or uses browser back/forward
- **THEN** the detail content follows the version represented by the URL

#### Scenario: Read original instructions
- **GIVEN** an accessible version with a root SKILL.md
- **WHEN** the reader opens Skill instructions
- **THEN** rendered and source views show that version's original file content without rewriting it

#### Scenario: Handle missing or unreadable instructions
- **GIVEN** a missing or failed file response
- **WHEN** the reader opens Skill instructions
- **THEN** an explicit empty or retryable error state is shown rather than another version's instructions

#### Scenario: Reset transient reading state
- **GIVEN** an expanded overview or source instruction view
- **WHEN** the selected version changes
- **THEN** transient overview and instruction reading state resets for the new version
