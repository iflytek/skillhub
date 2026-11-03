## ADDED Requirements

### Requirement: Sequential local handling and release preview
The plugin SHALL inspect local changes for the selected trusted installation before upgrade preparation, and SHALL expose local inspection directly from installation details. When modifications exist, the user SHALL first discard selected modifications or explicitly back up the exact local snapshot before proceeding to a separate release-change preview. Only the final explicit upgrade confirmation SHALL authorize replacement. The interface SHALL NOT present local changes and release changes as interchangeable parallel tabs.

#### Scenario: Inspect without upgrading
- **WHEN** the user opens local changes from installation details
- **THEN** the view compares the installed release with current local files and permits returning without entering an upgrade flow

#### Scenario: Modified installation upgrade
- **WHEN** the user requests an upgrade of a modified installation
- **THEN** local changes are handled first, successful backup alone does not modify the installation, and release differences are shown before a separate upgrade confirmation

#### Scenario: Unmodified installation
- **WHEN** the local snapshot matches the verified installed release
- **THEN** the user proceeds directly to the release preview without an unnecessary local-handling step

### Requirement: Verified original content and bounded differences
Both comparisons SHALL use exact releases verified against recorded source identity and fingerprint. Missing or unreadable baselines SHALL remain unavailable rather than being represented as unchanged. Difference content SHALL be loaded on demand with bounded byte, line and computation limits and explicit binary or truncated states. Each expanded file display SHALL have a maximum height of 320 CSS pixels on desktop and 240 CSS pixels on narrow layouts, with independently scrollable content and a visible file header.

#### Scenario: Wrong or unavailable original release
- **WHEN** the original archive is unavailable or its fingerprint does not match the installed release
- **THEN** comparison and discard operations remain unavailable and another source's same-named release is not substituted

#### Scenario: Large or binary content
- **WHEN** a changed file is binary or exceeds the preview limits
- **THEN** the view reports that state explicitly without sending unbounded content or treating the file as unchanged

### Requirement: Discard local file modifications without changing release
A confirmed discard operation SHALL restore modified or missing published files to the current installed release and delete only confirmed local additions. It SHALL NOT change the installed version, source, ownership, installation timestamp or sibling targets. A discard-all plan SHALL bind the complete reviewed file set. Invocation entry normalization SHALL preserve the selected installation's enabled or disabled state, and internal installation metadata SHALL NOT be treated as user-editable release content.

#### Scenario: Restore a modified file
- **WHEN** the user confirms discarding one modified published file
- **THEN** only that file is restored to the verified current-release bytes and the installed version remains unchanged

#### Scenario: Delete a local addition
- **WHEN** the user selects a locally added file for discard
- **THEN** confirmation explicitly states that the file will be deleted and cancellation preserves it

#### Scenario: Disabled installation
- **WHEN** a disabled installation's invocation file is restored
- **THEN** the original release content is restored through the trusted disabled-entry mapping without enabling the installation or creating a conflicting second entry

### Requirement: Snapshot-bound mutation authorization
Discard and overwrite execution SHALL revalidate the exact installation identity, normalized target, complete relevant file snapshot, metadata, ownership and permissions under the existing target-lock and inventory-CAS discipline. Drift SHALL invalidate the plan rather than extending the reviewed scope. Operations SHALL retain recoverable journals, idempotent request semantics and explicit failure outcomes. The shared inventory SHALL remain version 2 and supported older CLI behavior SHALL remain unchanged.

#### Scenario: Modification after confirmation preparation
- **WHEN** local files or installation identity change after a discard or overwrite snapshot is prepared
- **THEN** execution is rejected without applying the obsolete plan and the user is asked to review the current state

#### Scenario: Interrupted discard
- **WHEN** a discard transaction is interrupted during commit
- **THEN** the operation reports a recoverable or verified outcome and restart does not blindly replay a new user write intention

### Requirement: Durable backup before explicit overwrite upgrade
Persistent backup creation SHALL enforce a 100 MiB per-backup limit, a 1 GiB per-Host total limit and a maximum of 20 backups without silently evicting existing backups. Backing up and continuing SHALL copy and verify the complete confirmed local snapshot into a Host-private location outside skill discovery roots before opening the release preview. It SHALL NOT modify skill files or inventory. The final upgrade SHALL require a fresh explicit confirmation and revalidate that the verified backup and current snapshot still match. The old unconfirmed upgrade path SHALL continue refusing local drift. Backup failure SHALL prevent overwrite and remain visible with retry.

#### Scenario: Backup succeeds and preview is cancelled
- **WHEN** backup completes but the user cancels the subsequent release preview
- **THEN** the installation remains unchanged and the verified backup is retained

#### Scenario: Backup cannot be verified
- **WHEN** backup fails because of permissions, capacity, interruption or content mismatch
- **THEN** the plugin does not proceed to release preview or replacement and does not report a successful backup

#### Scenario: Local files change after backup
- **WHEN** files change between successful backup and upgrade submission
- **THEN** the old snapshot authorization is rejected and the new content must be reviewed and backed up separately

### Requirement: No release rollback or silent backup deletion
The plugin SHALL NOT expose version downgrade or post-upgrade release rollback in this capability. Local file discard SHALL refer only to the current installed release. Persistent user backups SHALL NOT be deleted automatically on cancellation, successful upgrade, cache eviction or restart. Cache storage SHALL be separate from persistent user backups, and capacity failure SHALL NOT silently evict user backups.

#### Scenario: Upgrade completes
- **WHEN** a newer release is installed successfully
- **THEN** the result does not offer restoring the previous release and retained backup data is not presented as an implemented rollback feature

### Requirement: Host-local processing and source isolation
Original release downloads SHALL reuse the existing Registry contract; local file contents SHALL NOT be uploaded to a Registry for comparison. Host-private APIs SHALL mediate all filesystem reads and mutations. Only current-source installations SHALL enter release-update previews and upgrades; trusted other-source installations MAY retain local inspection and discard using their own verified baseline.

#### Scenario: Source changes during an unsubmitted preview
- **WHEN** the active source changes before final upgrade acceptance
- **THEN** the old preview cannot authorize an upgrade through the new source

### Requirement: Compatible isolated file-reset journals
New file-reset journal schemas SHALL be stored outside the legacy operation directory so older consumers cannot classify them as corrupt legacy records and remove them. The new Host SHALL inspect pending new and legacy transactions before admitting any new write. Existing shared target-lock and inventory-lock discipline SHALL remain effective for older CLI consumers.

#### Scenario: New reset transaction is pending
- **WHEN** an interrupted file-reset operation has not been reconciled
- **THEN** the new Host blocks new installation, upgrade, removal and toggle writes until the pending state is verified, even if the legacy operation list is empty

#### Scenario: Older consumer sees shared inventory
- **WHEN** a supported older CLI reads inventory after a completed file reset
- **THEN** it can parse unchanged version-2 inventory and does not read or delete the isolated new journal as corrupt legacy data
