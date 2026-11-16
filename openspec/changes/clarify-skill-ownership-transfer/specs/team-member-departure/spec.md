## ADDED Requirements

### Requirement: REQ-DEP-01 Departed users SHALL lose internal access

A user who is not a member of a TEAM namespace SHALL NOT read, preview or download internal content of that namespace's Skills: PRIVATE and NAMESPACE_ONLY content, unpublished versions, drafts and protected files. This SHALL apply to Skills they own or used to own, and to historical records. Public published content SHALL stay available under the normal public rules. Separate administrator authority SHALL be evaluated as today. The rule SHALL apply to browser sessions and Tokens.

This replaces the read-only exception in REQ-OWN-02 (delivered in #975). Release notes and user-facing messages SHALL explain the change.

#### Scenario: Removed owner opens their PRIVATE Skill
- **WHEN** a removed user requests a PRIVATE Skill they still own
- **THEN** the request is denied, and the page explains that access ended when they left the namespace

#### Scenario: Public version
- **WHEN** the same user downloads a PUBLIC published version of that Skill
- **THEN** the download works as for any user

### Requirement: REQ-DEP-02 A new member removal SHALL convert the user's Skills

When a user is removed from a TEAM namespace after this feature is enabled, the system SHALL, in the same operation:

- make every Skill they own in that namespace namespace-owned with no maintainer, together with active global copies;
- clear their maintainer assignments in that namespace, without assigning a replacement;
- mark their pending handover requests in that namespace `INVALIDATED` and notify the other party.

Visibility SHALL NOT change; PRIVATE stays PRIVATE. Removal SHALL NOT wait for, or be blocked by, scans, reviews or handover requests. Conversion SHALL NOT happen twice for the same removal, and an incomplete conversion SHALL NOT keep or restore the removed user's access. The audit record SHALL name the operator who removed the member, not the removed user.

This replaces the "owner unchanged" part of REQ-OWN-01 for new removals.

#### Scenario: Remove a member with a pending review
- **WHEN** an admin removes a member whose Skill has a version waiting for review
- **THEN** the removal completes at once, the Skill becomes namespace-owned, and the review stays for the reviewer

### Requirement: REQ-DEP-03 In-flight work SHALL continue safely after removal

Scans SHALL continue. Pending reviews SHALL stay. When a reviewer decides, the system SHALL re-check current conditions, including whether the submitter may still submit for the Skill. The system SHALL NOT auto-approve, and SHALL NOT replace the submitter's authority with a worker or admin identity. Automatic approval after safe scans SHALL NOT run for a submitter who lost the right. When a review cannot continue, the reviewer SHALL see why (**proposed default:** approval is refused with a reason; the reviewer can reject; no new review state).

#### Scenario: Approve after the submitter left
- **WHEN** a reviewer approves a version submitted by a user who has since been removed
- **THEN** the approval is refused with a clear reason, and nothing is published

### Requirement: REQ-DEP-04 Account disabling SHALL NOT change ownership

Disabling an account while membership remains SHALL NOT convert ownership. Existing authentication rules apply. The namespace OWNER MAY use recovery handover. Re-enabling an account or re-adding a member SHALL NOT give back Skills that were converted or handed over.

This replaces the "regain maintenance" part of REQ-OWN-04 for Skills that are no longer user-owned by the returning user.

#### Scenario: Disabled member
- **WHEN** an active member's account is disabled
- **THEN** their Skills stay user-owned, and the namespace OWNER can start recovery

### Requirement: REQ-DEP-05 Historical records SHALL be remediated explicitly

The upgrade SHALL NOT convert existing Skills just because their owner is no longer a member. Authorized namespace OWNER/ADMIN and SUPER_ADMIN users SHALL be able to list Skills in their scope whose owner is no longer a member, and to convert them to namespace ownership, start a maintainer assignment, or perform cleanup they are already allowed to do. Each action SHALL write an audit record with the real operator, reason and result. Authorship SHALL NOT be backfilled by guessing.

#### Scenario: Upgrade with departed owners
- **WHEN** the upgrade runs while some owners are no longer members
- **THEN** their Skills stay user-owned, the owners lose internal access under REQ-DEP-01, and the Skills appear in the remediation list

### Requirement: REQ-DEP-06 Interfaces SHALL explain departure

- **My Skills** SHALL show personally owned Skills separately from maintained namespace-owned Skills, plus incoming and outgoing handover requests.
- **Skill detail** SHALL show provenance, the current responsible user, the handover entry and the request history.
- **Namespace management** SHALL list Skills without a maintainer, with links to authorized actions.
- **Member removal** SHALL show the number of affected Skills, the immediate loss of access, and that content is kept and converted.
- **Old links and notifications** SHALL explain removal or handover without exposing PRIVATE content or suggesting the Skill was deleted.

This replaces the read-only message of REQ-OWN-07.

#### Scenario: Remove dialog
- **WHEN** an admin opens the remove-member dialog for a user who owns 3 Skills in the namespace
- **THEN** the dialog says 3 Skills will become namespace-owned and the user loses internal access at once
