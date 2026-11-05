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

### Requirement: Single overview editing surface
The publish form MUST display the overview as a read-only preview with an Edit overview action at the field header's upper right, regardless of package or custom origin. The same placement MUST apply to populated, empty, and mobile states. A package fallback MUST be shown when available rather than treated as empty. Merely opening or completing an unchanged editor MUST NOT convert the default into a custom override.

#### Scenario: Edit without submitting
- **GIVEN** an overview preview in the publish form
- **WHEN** the author opens the editor, changes its local draft, and completes editing
- **THEN** the form preview updates without publishing or submitting a review
- **AND** cancelling an edited draft requires confirmation and preserves the preceding form value

#### Scenario: Preserve content between editor modes
- **GIVEN** Markdown content in the overview editor
- **WHEN** the author switches visual, source, or publication-preview modes
- **THEN** the same content is retained and the preview uses the detail rendering rules
- **AND** unsupported visual syntax remains recoverable in source mode rather than silently discarded

#### Scenario: Reject an oversized overview
- **GIVEN** an overview draft exceeding 20,000 characters
- **WHEN** the author tries to complete editing
- **THEN** completion is blocked with a visible limit error and the draft remains available for correction

#### Scenario: Complete immediately after an edit
- **GIVEN** a native editor input or touch-formatting action whose change callback has not yet run
- **WHEN** the author completes editing, changes mode, or requests cancellation immediately
- **THEN** the latest native content determines completion, mode content, length validation, and unsaved-change confirmation
- **AND** leaving without modifications does not convert package documentation into a custom override

#### Scenario: Return focus after cancellation
- **GIVEN** the author entered through Edit overview and changed the draft
- **WHEN** the author cancels and confirms discarding the changes
- **THEN** the form retains its preceding content and keyboard focus returns to Edit overview

#### Scenario: Preview a package before submission
- **GIVEN** an author selects a ZIP or a supported folder selection
- **WHEN** the package structure preview is pending, fails, or succeeds
- **THEN** each state has a distinct recoverable presentation
- **AND** structural parsing is not presented as completed security scanning, review, or publication

### Requirement: Independent web presentation sources
The web publish form MUST independently retain package-default or custom state for the summary and overview, without requiring a visible source-mode switch. Editing a value MUST deliberately create its custom state; unchanged default content MUST remain package-derived. The chosen effective values MUST be nonblank and within their existing respective limits before a version is submitted. This web validation MUST NOT make the optional package metadata fields mandatory for CLI or API uploads.

#### Scenario: Replace a package without overwriting edits
- **GIVEN** custom presentation drafts in the current form
- **WHEN** the author chooses another package or its preview completes asynchronously
- **THEN** modified values remain available and default fields follow the current package
- **AND** the other field's custom/default state does not change

#### Scenario: Restore one field to its package default
- **GIVEN** a custom field with a Restore package content action
- **WHEN** the author requests restoration and cancels the confirmation
- **THEN** its current content is preserved
- **WHEN** the author confirms restoration
- **THEN** only that field returns to the current package default
- **AND** a later package change follows its new default rather than inheriting the old default text

#### Scenario: Submit one package field and one custom field
- **GIVEN** any of the four independent source combinations
- **WHEN** the author submits a valid version
- **THEN** only the deliberately chosen custom values are frozen as custom overrides
- **AND** package values retain default-source behavior instead of being silently copied into custom overrides

#### Scenario: Validate missing effective content
- **GIVEN** a selected source with no usable content
- **WHEN** the author requests confirmation
- **THEN** a field error and a navigable error summary identify the missing content
- **AND** the file, source choices and drafts remain available for correction

### Requirement: Observable submission and recovery
Submission progress, accepted receipts and failures MUST be mutually exclusive. The form MUST prevent duplicate writes while submitting and preserve drafts on failure. Receipt text MUST follow the returned version state and MUST NOT describe an unpublished version as published.

#### Scenario: View the submitted version
- **GIVEN** an accepted submission receipt
- **WHEN** the author chooses to view this version
- **THEN** read-only detail navigation selects the receipt's namespace, skill and version rather than another published version

#### Scenario: Recover from a duplicate version
- **GIVEN** a version-already-exists response
- **WHEN** the author returns to replace the package
- **THEN** the form retains its presentation drafts and source choices and focuses the package replacement control
- **AND** the system does not silently change the version or automatically resubmit

#### Scenario: Handle an unknown submission outcome
- **GIVEN** a timeout or interrupted transport whose write outcome is not confirmed
- **WHEN** the result is displayed
- **THEN** the author is directed to inspect My Skills before retrying
- **AND** the client does not automatically replay the mutation or claim that no version was created

### Requirement: Contextual package upload control
The publish form MUST use the A1 primary-form and publishing-settings layout, with presentation fields after package and basic information. On narrow screens settings MUST precede presentation content. A selected package MUST replace the large empty dropzone with an actionable file row.

#### Scenario: Select a package accessibly
- **GIVEN** the empty upload control
- **WHEN** an enabled user clicks it or activates it with Enter or Space
- **THEN** the ZIP picker is opened once
- **AND** folder selection remains a distinct supported browser control

#### Scenario: Reject a replacement without losing the current package
- **GIVEN** a valid selected package and edited presentation content
- **WHEN** a replacement selection is cancelled, has an unsupported type or contains multiple ZIPs
- **THEN** an actionable rejection is shown where applicable, with the preceding package and custom content retained

#### Scenario: Report package-reading progress honestly
- **GIVEN** a selected package awaiting structure preview
- **WHEN** reading is pending, succeeds or fails
- **THEN** the file control shows distinct state and recovery actions
- **AND** it shows no invented progress percentage or publication-success claim
