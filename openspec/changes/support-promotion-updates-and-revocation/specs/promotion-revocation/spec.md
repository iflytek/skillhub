## ADDED Requirements

### Requirement: Authorized revocation requires platform review

The source skill owner or source namespace administrator SHALL be able to request revocation of the active global promotion. A platform reviewer SHALL approve or reject the request. A platform administrator MAY submit and directly execute the same audited action. The target SHALL be resolved by active relation and immutable Skill ID, never by slug alone.

#### Scenario: Unauthorized or unrelated target

- **WHEN** a user lacks permission on the source, or the supplied target is not the active derived global Skill
- **THEN** the system rejects the request without touching any skill

### Requirement: Approved revocation removes the global derivative and retains evidence

After approval, public discovery, detail, version resolution and download SHALL no longer expose the revoked global Skill. Its namespace/slug coordinate SHALL be available for a later normal application, subject to current collision checks. The team Skill and all of its versions/files SHALL remain usable. Promotion and revocation request history and audit records SHALL remain available to authorized administrators.

#### Scenario: Shared source and target storage

- **GIVEN** promoted global file records reference object keys also used by the team source
- **WHEN** revocation is approved
- **THEN** the global records are removed and the shared objects remain readable by the team source

#### Scenario: Re-promotion after revocation

- **WHEN** the original owner submits a new first-promotion request
- **THEN** the request follows ordinary platform review and creates a new global Skill identity if the coordinate is free
- **AND** an unrelated existing global Skill at the same coordinate cannot be overwritten

#### Scenario: Previously installed copies

- **WHEN** revocation is requested
- **THEN** the UI warns that already downloaded files cannot be recalled and an old coordinate may be claimed by a future Skill

### Requirement: Revocation and pending updates are consistent

Approval of revocation SHALL atomically close or invalidate pending updates against the removed global Skill, so a stale reviewer action cannot recreate or modify that Skill. Repeated approval SHALL not delete another Skill that later reuses the coordinate.

#### Scenario: Stale update approval after revocation

- **GIVEN** an update request is pending for a global Skill being revoked
- **WHEN** the revocation is approved and a reviewer later attempts to approve the old update
- **THEN** the old update cannot publish or recreate the removed global Skill
