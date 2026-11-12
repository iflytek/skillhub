## ADDED Requirements

### Requirement: Namespace-local session-managed member grant
The system SHALL provide one default-absent team-only automatic publication permission per ordinary MEMBER
in an ACTIVE TEAM namespace. Only that namespace's OWNER or ADMIN SHALL grant or revoke it through the
session-based member-management entry. Grants SHALL NOT change roles or confer review/delete/manage rights.
Every API token SHALL be denied grant management regardless of scopes or account roles.

#### Scenario: Unauthorized grant
- WHEN a member, another namespace's manager or an API token attempts to change a grant
- THEN the request is rejected and the grant and pending tasks remain unchanged.

#### Scenario: Isolated default and authorized management
- GIVEN an ACTIVE TEAM with an OWNER, ADMIN and ordinary MEMBER
- WHEN the OWNER or ADMIN grants that MEMBER
- THEN only that member in that namespace receives a fresh grant identity, without changing role.
- AND existing members and other namespaces remain ungranted by default.

### Requirement: Restrict eligibility to ordinary team-only publication
The system SHALL capture an active grant identity and accepted submit capability only on new ordinary submissions
of a new NAMESPACE_ONLY Skill or a same-visibility update to an already NAMESPACE_ONLY Skill.
Web and token submissions SHALL use the same policy; tokens SHALL still require submit capability.
Visibility changes, PRIVATE confirmation, PUBLIC and promotion SHALL NOT qualify.
Specialized workflows SHALL retain existing rules; rerelease SHALL reapply the same boundaries.

#### Scenario: Team-first and same-visibility update
- GIVEN a granted member and an authorized browser session or submit-capable token
- WHEN they submit a new team-only Skill or an already team-only Skill's new version
- THEN a pending review task captures the active grant identity and original submit capability.
- AND the version is not published before its current scan completes safely.

#### Scenario: Upload-based visibility expansion
- GIVEN an existing PRIVATE Skill owned by a granted member
- WHEN a new version targets NAMESPACE_ONLY
- THEN the submission follows normal review and does not capture automatic eligibility.

#### Scenario: Capability limits remain independent
- GIVEN a granted member with a read-only token
- WHEN the token uploads
- THEN the upload is rejected without changing Skill/version state.
- AND a grant never supplies PRIVATE confirmation or other missing token capability.

### Requirement: Current SAFE scan and atomic decision
Automatic approval SHALL require a completed explicit SAFE verdict for that version's current scan round,
ACTIVE TEAM, current valid membership and account, original submit permission, matching unrevoked grant,
uncancelled in-scope pending task/version and all existing governance restrictions.
No disabled, failed, unsafe, timed-out, unknown or stale scan SHALL auto-publish.
Competing manual decisions, withdrawal, superseding uploads, revocation and callbacks SHALL serialize atomically.

#### Scenario: Duplicate or late completion
- WHEN a completed scan callback is replayed or an old round completes after a newer scan or lifecycle action
- THEN it cannot republish, move latest again or emit another publication event.

#### Scenario: Governance or review race
- WHEN automatic approval races a manual decision, withdrawal, superseding upload or governance restriction
- THEN only the current valid task can complete once and the latest pointer identifies the correct published version.

### Requirement: Irreversible fallback with normal notifications
Grants SHALL affect new submissions only. Revocation, removal or loss of eligibility SHALL irreversibly cancel
pending automatic eligibility, preserving any scan in progress and returning work to normal gates.
Regranting SHALL NOT revive old tasks. Published versions SHALL NOT be withdrawn by grant revocation.
Fallback tasks SHALL be discoverable in the normal review queue and emit the existing ReviewSubmittedEvent
exactly once, respecting existing notification preferences and platform settings.

#### Scenario: Revoke and regrant during scanning
- GIVEN an eligible task still scanning
- WHEN its grant is revoked and later granted again
- THEN scanning continues, the old task stays manual and its normal-review notification occurs once.
- AND replaying a SAFE callback cannot restore its automatic eligibility.

#### Scenario: Accepted credentials later expire
- GIVEN work legally accepted with submit capability
- WHEN its token expires or is revoked, or its browser session ends
- THEN accepted work retains the original capability ceiling and rechecks current membership/grant.
- AND new requests with invalid credentials are rejected, while a privileged retry cannot add capability.

### Requirement: Structured system decision
The system SHALL retain review tasks with a structured HUMAN or SYSTEM_AUTOMATIC decision method.
System approval SHALL NOT identify the submitter as a human reviewer.
Review list/detail/history and outbound events SHALL expose the method; automatic records SHALL link namespace,
member, version, scan round, grant identity and decision time.

#### Scenario: System approval record
- WHEN an eligible version completes its current SAFE scan and is automatically approved
- THEN the task is APPROVED with SYSTEM_AUTOMATIC, no human reviewedBy, grant/scan basis and decision time.
- AND review/history/event consumers can distinguish it from a HUMAN approval.

### Requirement: Existing member management and audit
Existing namespace member management SHALL show role, grant status and authorized grant/revoke controls,
with localized explanation covering browser submissions and all submit-capable tokens in this namespace.
Grant/revoke audit SHALL retain actual operator, namespace/member and before/after identity.

#### Scenario: Manage a member grant
- WHEN an authorized manager grants or revokes in the existing members page
- THEN the current status is refreshed and the audit preserves the actual actor and before/after values.
- AND no separate page, token scope or namespace switch is required.

#### Scenario: Migration and startup
- WHEN Flyway migrates an empty PostgreSQL database or upgrades representative pre-V124 data
- THEN existing members/tasks default to no automatic eligibility and the application starts successfully.
