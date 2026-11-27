## ADDED Requirements

### Requirement: REQ-DEP-01 Departed users SHALL lose internal access

A user who is not a member of a TEAM namespace SHALL NOT read, preview or download its internal content: PRIVATE and NAMESPACE_ONLY content, unpublished versions, drafts and protected files. Current or historical ownership SHALL NOT keep that access. Public published content stays public, and separate administrator authority is evaluated as today. This applies to sessions and Tokens from the moment the removal commits, and replaces the read-only exception in REQ-OWN-02.

#### Scenario: Removed owner opens their PRIVATE Skill
- **WHEN** a removed user requests a PRIVATE Skill they still own
- **THEN** the request is denied, and the page explains that access ended when they left the namespace

#### Scenario: Public version
- **WHEN** the same user downloads a PUBLIC published version of that Skill
- **THEN** the download works as for any user

### Requirement: REQ-DEP-02 Member removal SHALL NOT wait for conversion or in-flight work

After this feature is enabled, removing a member SHALL succeed without waiting for scans, reviews, handover requests or the conversion of their Skills. Removal SHALL NOT require one unbounded transaction over all affected Skills. The user's pending handover requests in that namespace SHALL become `INVALIDATED`. This replaces the "owner unchanged" part of REQ-OWN-01 for new removals.

#### Scenario: Remove a member with a pending review
- **WHEN** an admin removes a member whose Skill has a version waiting for review
- **THEN** the removal completes at once, and the review stays for a human reviewer

### Requirement: REQ-DEP-08 Removal SHALL record what it converts

The removal SHALL record the removal event and the affected records: Skills the user owns in that namespace and their maintainer assignments there. Each recorded Skill SHALL become namespace-owned with no maintainer, and each recorded assignment SHALL be cleared with no replacement. Global copies follow their source (REQ-OM-05). Visibility SHALL NOT change. Audit records SHALL name the operator who removed the member and reference the removal event.

#### Scenario: Conversion result
- **WHEN** a member who owns a PRIVATE Skill and maintains another Skill is removed
- **THEN** the owned Skill becomes namespace-owned with no maintainer and stays PRIVATE, and the other Skill loses its maintainer

### Requirement: REQ-DEP-09 Conversion SHALL be observable, idempotent and retryable

The membership deletion, the removal event and the registration of affected records SHALL commit together, or not at all. Conversion, notifications and reindexing SHALL run after that commit and MAY run in bounded batches. Its progress SHALL be visible. A retry SHALL touch only the records listed for that removal event, and only while they are still in the state recorded at removal. Failed or incomplete conversion SHALL NOT keep or restore the removed user's access or maintenance rights.

The temporary restriction on maintenance rights SHALL be bound to the removal event and the responsibility it recorded. It SHALL end once that responsibility changes through a later valid handover or governance assignment, which SHALL then grant the rights of the new responsibility. Rejoining alone SHALL NOT end it. Retrying or skipping the old item SHALL neither clear the new responsibility nor restrict its rights. Notifications SHALL be sent after each change commits.

#### Scenario: Retry after a partial failure
- **WHEN** conversion stops after some Skills, and meanwhile another recorded Skill was reassigned through governance
- **THEN** a retry converts only the remaining unchanged Skills, and leaves the reassigned Skill alone

#### Scenario: Rejoin while conversion is incomplete
- **WHEN** the removed user rejoins before conversion of their Skills has finished
- **THEN** they do not regain maintenance rights over the recorded Skills, conversion still completes, and any reassignment goes through handover

#### Scenario: Rejoin, then a valid reassignment, then the old item is retried
- **WHEN** A is removed while maintaining Skill 301, rejoins before the item for 301 is processed, and a namespace ADMIN then reassigns 301 to A through governance and A accepts
- **THEN** A holds the maintenance rights of the new assignment at once, and a later retry of the old item marks it skipped without clearing A or restricting A's rights

#### Scenario: Removal fails part-way
- **WHEN** registering the affected records fails during a removal
- **THEN** the member is not removed, and no removal event or record is kept

### Requirement: REQ-DEP-03 Applications SHALL survive responsibility changes

When responsibility for a Skill changes through handover, removal or conversion, an application that was legitimately accepted, and whose material has not been replaced, SHALL keep its original submitter, exact material and review history. The change SHALL remove any automatic-approval eligibility, including automatic approval after safe scans, and leave the application for normal human review. No mandatory "new maintainer confirms" step SHALL be added.

#### Scenario: Auto-approval after a handover
- **WHEN** a version qualified for automatic approval after safe scans, and the Skill is handed over before the scans finish
- **THEN** the version is not approved automatically and waits for a human reviewer

### Requirement: REQ-DEP-10 A human reviewer SHALL decide with current checks

An authorized reviewer MAY approve or reject such an application after checking current authorization, Skill state, scan results, target visibility and every other applicable condition. A background worker SHALL NOT use an administrator identity to supply missing submitter authority. Reviewer privileges SHALL NOT be added and review restrictions SHALL NOT be relaxed. Each application type keeps its own approval requirements.

#### Scenario: Approve after the submitter left
- **WHEN** a reviewer approves a version submitted by a user who has since been removed, the material is unchanged and every current check passes
- **THEN** the version is approved, and the original submitter stays recorded

#### Scenario: Visibility request after handover
- **WHEN** a pending request to make a Skill PUBLIC is still open after a handover
- **THEN** it still needs its normal approval, and the handover did not widen visibility

### Requirement: REQ-DEP-04 Account disabling SHALL NOT change ownership

Disabling an account while membership remains SHALL NOT convert ownership. Existing authentication rules apply. The namespace OWNER MAY use recovery handover. Re-enabling an account or re-adding a member SHALL NOT give back Skills that were converted or handed over. Existing restrictions on removing a namespace OWNER SHALL still apply. This replaces the "regain maintenance" part of REQ-OWN-04 for Skills the returning user no longer owns.

#### Scenario: Disabled member
- **WHEN** an active member's account is disabled
- **THEN** their Skills stay user-owned, and the namespace OWNER can start recovery

### Requirement: REQ-DEP-05 Historical records SHALL be remediated explicitly

The upgrade SHALL keep historical records and SHALL NOT convert Skills just because their owner is no longer a member. Namespace OWNER/ADMIN and SUPER_ADMIN users SHALL be able to list such Skills in their scope, and to convert them, start a maintainer assignment, or do cleanup they are already allowed to do. Each action SHALL be explicit and audited with the real operator, reason and result. Authorship SHALL NOT be backfilled by guessing.

#### Scenario: Upgrade with departed owners
- **WHEN** the upgrade runs while some owners are no longer members
- **THEN** their Skills stay user-owned, the owners lose internal access under REQ-DEP-01, and the Skills appear in the remediation list

### Requirement: REQ-DEP-06 Interfaces SHALL explain departure

My Skills SHALL separate owned Skills from maintained Skills and show handover requests. Skill detail SHALL show provenance, the responsible user, the handover entry and request history. Namespace management SHALL list Skills without a maintainer and unfinished conversions. The remove-member dialog SHALL show affected Skills and the immediate loss of access. Old links SHALL NOT expose PRIVATE content or suggest deletion. This replaces REQ-OWN-07's message.

#### Scenario: Remove dialog
- **WHEN** an admin opens the remove-member dialog for a user who owns 3 Skills in the namespace
- **THEN** the dialog says 3 Skills will become namespace-owned and the user loses internal access at once

### Requirement: REQ-DEP-07 Issued download links SHALL be bounded by their expiry

Every new protected request SHALL apply current authorization. A presigned download URL issued before removal or handover MAY keep working until it expires, as set by `SKILLHUB_STORAGE_S3_PRESIGN_EXPIRY` (default `PT10M`, deployments may change it). Files already downloaded cannot be recalled. Release notes and UI messages SHALL say exactly this.

#### Scenario: Link issued before removal
- **WHEN** a user gets a presigned download URL and is removed from the namespace five minutes later
- **THEN** the URL may work until its configured expiry, and any new download request from that user is denied
