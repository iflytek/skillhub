## ADDED Requirements

### Requirement: Browse and inspect anonymous skills
The plugin SHALL provide an initial skill list, keyword search, and details with actual coordinates, source, resolved version and available description. The plaza SHALL request 12 items per page from the anonymous paged catalog and append pages only on explicit load-more actions. Legacy bounded search SHALL remain limited to 20 returned items with a visible range limitation. The plugin SHALL NOT invent catalog fields or pagination.

#### Scenario: Initial browsing
- **WHEN** the user opens the plaza without a search query
- **THEN** the plugin displays a list returned by the Registry or an explicit loading, empty or error state

#### Scenario: Search and detail return
- **WHEN** the user searches, opens a result, and closes its details
- **THEN** the search query, results and browsing position are retained

#### Scenario: Limited search response
- **WHEN** the API only returns a limited result set
- **THEN** the UI displays at most 20 skills, identifies the returned range, and does not imply that all skills or invented pages are available

#### Scenario: Newer keyword query wins
- **WHEN** the user submits a new keyword while an earlier search is still pending
- **THEN** the earlier response is cancelled or ignored and cannot replace the results of the latest submitted keyword

#### Scenario: Load more and return from detail
- **WHEN** the user loads another page and opens then closes a skill
- **THEN** loaded pages, query, sort, scroll and focus are retained; a failed later page preserves existing results and offers retry

#### Scenario: Optional card metrics and avatar fallback
- **WHEN** a skill has no image or some presentation metrics are absent
- **THEN** the card uses a stable first-character avatar and hides absent metrics without fabricating zero ratings or expanding the compact card

#### Scenario: Legacy catalog route
- **WHEN** the first paged catalog request returns 404 or 405
- **THEN** the plugin may use the original bounded search and identifies its limited mode without load-more or effective sort controls; other failures remain errors

### Requirement: Explicit trusted installation target
The plugin SHALL install only after an explicit user action, default to user scope, and allow project scope only with trusted Host project context. It SHALL preserve existing Host policies and SHALL NOT accept arbitrary target paths from UI input.

#### Scenario: Remote Host user installation
- **WHEN** the user chooses the default target in a remote single-user DSH session
- **THEN** the UI identifies installation on the DSH Host and installs there rather than on the browser computer

#### Scenario: No current project
- **WHEN** a trusted current project is unavailable
- **THEN** project installation is unavailable and user scope remains the default

#### Scenario: Host denies action
- **WHEN** the Host rejects an installation
- **THEN** the plugin reports the restriction without bypassing policy or creating its own permission workflow

### Requirement: Complete compatible package installation
The plugin SHALL validate the complete skill archive, fingerprint, filesystem boundaries and DSH format before commit. It SHALL preserve package resources and invocation restrictions, reuse compatible metadata, inventory and locks, and SHALL NOT execute package scripts during installation.

#### Scenario: Resource-bearing package
- **WHEN** a compatible skill includes resources and scripts
- **THEN** installation preserves those files without executing them and records the source, version and target

#### Scenario: Invalid archive or incompatible DSH format
- **WHEN** an archive violates path/size/fingerprint constraints or the skill is incompatible with the supported DSH format
- **THEN** installation fails with a specific reason before committing the skill and does not rewrite the package to appear compatible

### Requirement: Non-destructive install and honest recovery
The plugin SHALL revalidate targets under compatible locks, avoid duplicate submissions, and block conflicting existing content. First-time installation SHALL NOT overwrite existing content; upgrade and uninstall SHALL use the separate protected plans defined below.

#### Scenario: Identical installation already exists
- **WHEN** the same source, version and target are already installed with intact content
- **THEN** the plugin reports already installed without duplicating or overwriting the installation

#### Scenario: Conflicting target
- **WHEN** a target has another source, version, local modifications or unmanaged content
- **THEN** the plugin blocks the installation and leaves the existing content unchanged

#### Scenario: Concurrent CLI mutation
- **WHEN** the target changes between inspection and installation
- **THEN** lock-protected revalidation prevents stale installation plans from overwriting the change

#### Scenario: Commit or record failure
- **WHEN** file or inventory commit fails
- **THEN** the plugin attempts supported recovery and reports the actual outcome including any incomplete recovery

### Requirement: Separate installation and discovery results
The plugin SHALL distinguish file installation from DSH discovery and execution, and SHALL NOT restart the Host automatically.

#### Scenario: Installed but discovery unconfirmed
- **WHEN** installation succeeds without reliable discovery evidence
- **THEN** the UI reports installed and discovery unconfirmed rather than ready or executed

#### Scenario: Conversation use is a separate fact
- **WHEN** an installed skill is discovered and the user returns to a conversation
- **THEN** the plugin reports only observed installation and discovery states; actual skill use is established from a separate conversation observation and is not inferred from discovery

#### Scenario: Current details changed during installation
- **WHEN** the user opens another skill while an installation is running
- **THEN** completion remains associated with the original skill and target and does not replace the new skill's state

### Requirement: Installed projection and scoped actions
The plugin SHALL provide installed records for the active Registry within trusted user and current-project targets, using existing metadata and inventory. It SHALL distinguish local presence, inconsistent records and incomplete reads without claiming a complete DSH discovery catalog. Upgrade and uninstall actions SHALL bind to verified installed targets.

#### Scenario: Two scopes installed
- **WHEN** a skill is installed in both user and current-project targets
- **THEN** each target is visible separately with its installed version and local state

#### Scenario: Missing directory or partial read
- **WHEN** installation records reference missing files or some allowed targets cannot be read
- **THEN** the list identifies inconsistency or partial results instead of silently deleting records or showing an empty complete list

#### Scenario: Independent scope remains available
- **WHEN** one target scope is unavailable but the shared inventory and another target scope are independently verified
- **THEN** only the unavailable scope is blocked; an unreadable shared inventory still prevents inferring absence in either scope

#### Scenario: Installed references survive list refresh
- **WHEN** a complete installed list contains more than 400 records or the same records are read repeatedly across sessions
- **THEN** every returned item reference remains usable within its valid context; listing and reference retention do not immediately invalidate an item in the response, and changed source, session or installed identity is still rejected

#### Scenario: Installed detail while offline
- **WHEN** an installed skill detail is opened while the Registry is unavailable
- **THEN** local identity, installed version and target remain visible with unavailable remote content, and the plugin does not substitute the latest version or allow unverified installation

#### Scenario: Lost installation acceptance response
- **WHEN** a Client loses the startInstall response
- **THEN** retrying the same request key and parameters recovers the existing operation when available; missing state after Host restart requires reconciliation rather than an automatic duplicate write

### Requirement: Explicit immutable mutation plans
The plugin SHALL prepare upgrade and uninstall plans on the Host with exact source, identity, target set, versions, baseline, ownership and a five-minute expiry. Only a ready, unexpired plan explicitly confirmed by the user SHALL execute; execution SHALL revalidate the complete before-state under compatible locks.

#### Scenario: Plan context changes
- **WHEN** source, project, target files, metadata, inventory target set or ownership changes after planning
- **THEN** execution rejects the stale plan without mutating the target and requires a new plan

#### Scenario: Repeated submission
- **WHEN** the same request key and parameters are submitted again
- **THEN** the existing operation is returned, while reusing the key with different parameters is rejected

#### Scenario: Expired or consumed plan
- **WHEN** an unsubmitted plan is older than five minutes or a consumed plan is submitted as a new operation
- **THEN** the Host rejects it rather than extending or reusing its authority

### Requirement: Protected explicit upgrade
The plugin SHALL check for updates only on user request and SHALL upgrade only to a resolved newer exact version with a matching fingerprint. It SHALL preserve invocation restrictions and resources, refuse local drift and shared Suite ownership, and SHALL NOT silently downgrade or replace changed content under the same version.

#### Scenario: No valid newer release
- **WHEN** the candidate is older, incomparable, or the same version with different content
- **THEN** upgrade is blocked and local files remain unchanged; an identical current release is reported as unchanged

#### Scenario: Independent target releases
- **WHEN** a skill is installed in user and project scopes and the user confirms an upgrade of the project installation
- **THEN** only the project files and target release record change; user files, release, timestamps and ownership remain unchanged

#### Scenario: Unselected targets outside current context
- **WHEN** the same source coordinate also has another project, Agent or Suite-owned installation
- **THEN** that sibling is not included in the upgrade and is preserved; shared ownership or an alias of the selected physical target still blocks the operation

#### Scenario: Local content modified or baseline missing
- **WHEN** existing content differs from its recorded baseline or has no usable baseline
- **THEN** upgrade is blocked with a reason and no force option

### Requirement: Exact local uninstall
The plugin SHALL uninstall only one confirmed trusted target, without remote deletion or changing other targets. It SHALL verify identity, path, baseline and ownership and SHALL isolate files before inventory commit so failure can be reconciled. It SHALL NOT treat arbitrary filesystem errors as absence.

#### Scenario: Two installed scopes
- **WHEN** the user confirms uninstall of the user-level row while a project-level copy exists
- **THEN** only the user target and its exact inventory reference are removed, and the project copy remains

#### Scenario: Offline uninstall
- **WHEN** the Registry is unavailable but local ownership and baseline are verified
- **THEN** uninstall can proceed without Registry requests or login

#### Scenario: Local changes or shared ownership
- **WHEN** the target is modified, its ownership is unknown, or it has Suite/shared ownership
- **THEN** uninstall is blocked and no directory or source reference is deleted

#### Scenario: Missing directory versus access error
- **WHEN** target inspection returns ENOENT
- **THEN** only a separately confirmed stale-record cleanup may proceed, whereas access or I/O errors remain failures and do not remove records

### Requirement: Honest recovery and lifecycle results
All file mutations SHALL record enough local operation state to reconcile interrupted commits, use the same target and inventory lock discipline as the CLI, and report actual recovery and cleanup outcomes. Restart SHALL NOT blindly replay an upgrade or deletion.

#### Scenario: Inventory failure after quarantine
- **WHEN** uninstall has moved files aside but inventory commit fails
- **THEN** the Host attempts safe restoration and reports restored failure or recovery-required rather than claiming successful uninstall

#### Scenario: Cleanup failure after commit
- **WHEN** uninstall inventory commit succeeds but isolated files cannot be cleaned
- **THEN** logical removal is reported with a residual-file warning and retained recovery information

#### Scenario: Host restarts during mutation
- **WHEN** the process restarts with a nonterminal operation journal
- **THEN** the Host reacquires compatible locks, reconciles actual filesystem and inventory state, and blocks conflicting new mutations until the outcome is known

#### Scenario: Accepted installation interrupted before any staging intent
- **WHEN** a valid current-schema installation journal remains accepted or staging without a transaction after restart
- **THEN** explicit recovery holds the execution lease, validates the original trusted targets, and safely terminates the unexecuted request without creating target directories, replaying installation or deleting journal evidence; malformed journals and active executions are not settled this way

#### Scenario: Idempotent installation discovery
- **WHEN** an already-installed result was completed using a verified read-only baseline
- **THEN** discovery may be confirmed only after the original identity, exact version, fingerprint, ownership and trusted targets match before and after Host observation; missing historical baseline or changed content remains unconfirmed

#### Scenario: Another provider still supplies the same name
- **WHEN** uninstall removes one target but another source or scope still exposes the skill
- **THEN** the UI reports removal of the selected target without claiming global removal or termination of running tasks


#### Scenario: Journal fails after inventory commit
- **WHEN** inventory commit succeeded but the committed journal write or backup cleanup fails
- **THEN** the plugin preserves committed files and records and reports a warning, never restoring old files against new inventory; an unknown commit result is reconciled by rereading state before recovery

#### Scenario: CLI and plugin lock ordering
- **WHEN** CLI and plugin mutations address multiple targets including path aliases
- **THEN** both use the same canonical lock keys, deduplication and ordering, and the transaction core acquires each lock only once


#### Scenario: Accepted retry with expired references
- **WHEN** an accepted request is retried after restart or context change with the same owner, request key and original parameter digest
- **THEN** the Host returns the persisted original operation before evaluating new-write token validity, without starting another mutation; mismatched parameters are rejected and missing recovery evidence yields unknown


### Requirement: Public label filtering
The plugin SHALL display instance-provided public filter labels and apply an explicitly selected label to server-side catalog queries together with search, sorting and pagination. It SHALL NOT substitute version tags or hardcoded example categories.

#### Scenario: Change a label
- **WHEN** a user selects a different label
- **THEN** the catalog restarts at its first page and the query identity includes that label; returning from details preserves the selection

#### Scenario: Label filtering is unavailable
- **WHEN** the label directory is missing or a filtered catalog request fails
- **THEN** ordinary unfiltered browsing remains independently available, and a selected-label request never silently falls back to unfiltered results

#### Scenario: Change the source
- **WHEN** the configured source revision changes
- **THEN** the previous source's selected label and late responses cannot become the new source's active filter


### Requirement: Read-only current-session availability diagnosis
The plugin SHALL diagnose a selected managed installation or a named current-session skill without changing files, inventory, invocation policy or composer drafts. Diagnosis SHALL distinguish integrity, Host discovery and user/model invocation policy. See `../../skill-availability-design.md` for the interaction and bounded implementation scope.

#### Scenario: A different installation wins the same name
- **WHEN** the official Host resolves the selected skill name to a different resource identity
- **THEN** the selected installation is reported as shadowed, and any winner scope is shown only when independently established; the plugin SHALL NOT switch the target or invoke the winner automatically

#### Scenario: The same resource changed
- **WHEN** the resource identity is unchanged but its content or policy differs from the checked local package
- **THEN** the diagnosis reports changed or unverified state rather than claiming same-name shadowing

#### Scenario: Incomplete Host or filesystem evidence
- **WHEN** snapshot support is absent, a provider is incomplete, an approved root is unreadable, or context changes while checking
- **THEN** the plugin reports unknown or a stale-context failure, never inferring uninstalled from an empty list

#### Scenario: Skill has no current-source ownership
- **WHEN** a named Host winner cannot be exactly associated with a current-source managed installation
- **THEN** its source is described as unassociated and only observed invocation facts are exposed; the diagnosis SHALL NOT infer a registry, version or clean fingerprint, or offer mutation actions

#### Scenario: Invocation policies differ
- **WHEN** user invocation and model invocation have different values
- **THEN** the result preserves both values without treating one disabled policy as total unavailability

#### Scenario: Compact historical-card diagnosis
- **WHEN** a user checks availability from an installed card
- **THEN** the result belongs to that exact installation reference and selected scope; changing scope clears it, and a stale reference requires a fresh query rather than automatic rebinding

#### Scenario: Path-free result
- **WHEN** Host resources or errors contain paths, content, metadata or secrets
- **THEN** only explicitly allowed diagnosis fields reach the model and UI, and no raw resource or error is serialized
