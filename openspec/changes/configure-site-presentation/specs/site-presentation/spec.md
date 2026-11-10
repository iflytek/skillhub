## ADDED Requirements

### Requirement: Persist instance presentation without source patches
The system SHALL store presentation overrides in the existing settings database and preserve them across restarts and upgrades. Absent overrides SHALL keep the existing appearance.

#### Scenario: Default installation
- WHEN no administrator customized presentation
- THEN existing SkillHub name, brand image, landing layout, sections and footer remain available.

#### Scenario: Upgrade a customized installation
- GIVEN a saved custom name, homepage mode and image references
- WHEN the application is restarted or upgraded with persistent database and storage
- THEN the same settings and assets remain effective without modifying source.

### Requirement: Restrict management and public disclosure
Only SUPER_ADMIN SHALL modify presentation or upload brand assets. Public reads SHALL return only explicit presentation fields. Edits SHALL check the expected version and record an audit event.

#### Scenario: Stale save
- GIVEN two administrators loaded the same version
- WHEN one saves and the other submits the old version
- THEN the second save is rejected and the committed settings are not overwritten.

#### Scenario: Unauthorized write
- WHEN an anonymous or ordinary member submits settings or an image
- THEN the write is denied and the stored presentation remains unchanged.

### Requirement: Apply safe brand and homepage options
The UI SHALL apply instance display name, safe logo/favicon references, LANDING/CATALOG mode, localized plain hero text, section switches and footer visibility. Catalog data SHALL use existing visibility checks.

#### Scenario: Hide popular skills
- WHEN showPopular is false
- THEN the popular section is absent and its query does not execute.

#### Scenario: Invalid image or unsafe URL
- WHEN an upload is invalid, exceeds limits, or a submitted reference points outside controlled brand storage
- THEN validation rejects it and existing presentation remains unchanged.

### Requirement: Provide a recoverable editing flow
The UI SHALL distinguish local preview from saved settings, warn about unsaved changes, retain drafts after failed saves and allow group defaults to be restored before saving.

#### Scenario: Preview without publishing
- WHEN an administrator changes the draft and opens preview
- THEN visitors continue seeing the last saved configuration until Save succeeds.

#### Scenario: Save failure
- WHEN the save request fails
- THEN draft values remain editable and the UI offers an explicit retry/recovery action.
