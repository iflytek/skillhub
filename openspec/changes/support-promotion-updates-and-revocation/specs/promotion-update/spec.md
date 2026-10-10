## ADDED Requirements

### Requirement: Published team versions can update the linked global skill

An authorized source owner or source namespace administrator SHALL be able to submit a published team version for platform review against the currently linked global skill. The system SHALL derive the target from the approved promotion relation and SHALL NOT accept a client-selected unrelated target. Each submission SHALL require platform review.

#### Scenario: Review approves a subsequent promotion

- **GIVEN** a published team version and an active linked global skill
- **WHEN** an authorized user submits the version and a platform reviewer approves it
- **THEN** the same global Skill ID gets a new published version with the team's version string and source-version provenance
- **AND** the previous global versions remain unchanged
- **AND** the global skill's display name and summary reflect the source skill at approval time

#### Scenario: Team version is not published

- **WHEN** the source version is pending review, rejected, or yanked
- **THEN** submission or approval is rejected and the global skill is unchanged

### Requirement: Concurrent updates never overwrite a version

The system SHALL reject an update when the global skill already has the same version string, including a draft or pending version. Version strings SHALL NOT be ordered to decide eligibility; the most recently approved release becomes latest. The system SHALL recheck permissions, source/target status and version uniqueness at approval time.

#### Scenario: Global independent upload races with a promotion update

- **GIVEN** an independent global upload and a team promotion update are both pending
- **WHEN** they use different version strings and both are approved
- **THEN** both versions remain, and the later approval becomes latest
- **WHEN** they use the same version string
- **THEN** only one can publish and the other receives a conflict, without replacing bytes

#### Scenario: Version labels appear to move backward

- **GIVEN** the current global latest is v1.3 and the team candidate is v1.1, not already present globally
- **WHEN** the source user sees the version difference and confirms submission, and the reviewer approves
- **THEN** global v1.1 becomes latest by publish time and v1.3 remains in history

### Requirement: Submission and review are visible in existing product surfaces

The team skill detail SHALL show first-promotion or subsequent-update action, team and global current published versions, and pending/rejected/approved feedback. The existing admin promotion review surface SHALL distinguish initial and update requests and show the source version and current global version before review.

#### Scenario: Reviewer examines an update request

- **GIVEN** a submitted update request
- **WHEN** a platform reviewer opens the existing promotion review surface
- **THEN** the request is labeled as an update and shows the source version and current target version before approval
