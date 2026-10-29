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

### Requirement: Persistent managed-installation enablement
The plugin SHALL enable or disable exactly one current-source managed DSH installation at a trusted user or current-project location. Disablement SHALL persist across sessions by renaming the exact SKILL.md entry to SKILL.md.disabled while preserving entry bytes, resource paths and invocation policy. It SHALL NOT implement this contract as a conversation-only filter or execute package scripts. See `../../skill-toggle-design.md` for the storage protocol and implementation gates.

#### Scenario: Disable one of two installations
- **WHEN** user and project installations exist and the user confirms disabling the project installation
- **THEN** only that exact entry is renamed to the verified non-discovered alias, its inventory identity and version remain installed with disabled state, and user installation files and records remain unchanged

#### Scenario: Default scope does not select a mutation target
- **WHEN** a request to disable a skill matches multiple managed locations without an explicit location
- **THEN** the Host requires a target selection before preparing confirmation; the user-scope default for new installation SHALL NOT select the target

#### Scenario: Unmanaged or shared installation
- **WHEN** a selected target has external, unknown, Suite/shared or overlapping physical ownership
- **THEN** enabling or disabling is blocked without modifying files or claiming that installation

#### Scenario: Already in requested state
- **WHEN** the verified exact installation already has the requested enablement state
- **THEN** the plugin reports unchanged without moving files or creating another successful mutation

### Requirement: Enablement separate from discovery
The plugin SHALL represent persistent enablement, package integrity, current-session discovery and invocation policies as separate facts. Disabled installations SHALL remain visible in installed queries and SHALL NOT be treated as missing merely because the standard entry is absent.

#### Scenario: Same-name fallback after disablement
- **WHEN** the selected installation is disabled and another location or provider wins the same skill name
- **THEN** the result identifies only the selected location as disabled, reports a winner only from verified Host evidence, and neither disables nor silently uses the other installation

#### Scenario: Watcher unavailable or stale
- **WHEN** the filesystem transaction commits while watch is disabled, failed or discovery remains cached
- **THEN** the result reports the committed installation state and unconfirmed or observed discovery separately, without claiming instantaneous revocation, restarting the Host or terminating running tasks

#### Scenario: Custom discovery includes disabled entries
- **WHEN** a custom provider can still supply the renamed entry or its content
- **THEN** the plugin limits its disablement claim to the managed entry under the supported official filesystem provider, reports any verified current-session discovery separately, and never treats a filename convention or absent snapshot as proof of machine-wide exclusion

#### Scenario: Enablement preserves invocation policy
- **WHEN** a disabled package with restricted user or model invocation is successfully restored
- **THEN** its bytes and restrictions are preserved and it is not reported callable until the exact resource and relevant policy are independently verified

### Requirement: Confirmed exact enablement plans
Enablement mutations SHALL use immutable Host plans, real user confirmation, trusted context, compatible locks and exact before-state revalidation. Page and conversation entry points SHALL use the shared core without launching a CLI process. A natural-language target reference SHALL NOT itself authorize execution.

#### Scenario: Cancellation or changed context
- **WHEN** the user cancels or the source, session, project root, services, installation state, version, content or ownership changes before acceptance
- **THEN** the unaccepted plan cannot execute; changed context requires a fresh query and confirmation and cancellation leaves files and inventory unchanged

#### Scenario: Repeated confirmation and lost response
- **WHEN** an accepted toggle is retried with the same owner, request key and parameter digest after a response is lost
- **THEN** the original operation is returned without another movement; different parameters are rejected and missing outcome evidence remains unknown rather than triggering a new request

#### Scenario: Historical use action after disablement
- **WHEN** a historical card requests use or another mutation after the selected installation's state changes
- **THEN** the old reference is rejected without binding it to another location; a fresh query exposes the current state and disabled installations do not offer use

### Requirement: Recoverable enablement file transactions
The shared core SHALL persist entry-transition intent, validate both entry paths, acquire canonical target locks before the inventory lock and CAS only the selected target. Inventory commit SHALL determine logical success; recovery SHALL reconcile inventory and actual files without overwriting unrelated or conflicting content. Disabled installation directories SHALL remain reserved against other managed installations.

#### Scenario: Standard entry occupied
- **WHEN** enabling finds any existing content, link, conflicting claim or unreadable state at the standard entry path
- **THEN** restoration is blocked, the disabled entry and package and disabled record remain intact, and no merge, overwrite or alternate destination is chosen

#### Scenario: Disabled package drift
- **WHEN** a disabled package is missing, modified or lacks a usable integrity baseline
- **THEN** enabling and destructive mutations are blocked with an explicit integrity or unknown-state result rather than reconstructing or deleting content

#### Scenario: Crash after movement before inventory commit
- **WHEN** the entry has been renamed but the inventory still matches the before-state after interruption
- **THEN** recovery reacquires compatible locks and restores only when the non-overwriting inverse transition and baseline are verified safe; conflicts preserve evidence and require recovery rather than reporting success

#### Scenario: Inventory committed but completion journal failed
- **WHEN** inventory matches the after-state and files match the committed entry name despite a failed terminal journal write
- **THEN** reconciliation preserves the new state and reports the actual outcome with warning, never reverting files against committed inventory

#### Scenario: Unknown commit or ambiguous files
- **WHEN** CAS outcome is unknown or both standard and disabled entries exist or cannot be read reliably
- **THEN** the core rereads state under compatible locks and retains unresolved evidence without replaying the toggle or allowing conflicting new mutations

#### Scenario: Concurrent independent targets
- **WHEN** different installations change concurrently while one is being disabled
- **THEN** selected-target CAS preserves the other installation's latest version, state, ownership and files; an original path reserved by a disabled target is not treated as unclaimed

### Requirement: Disabled installation lifecycle
Disabled installations SHALL remain eligible for protected update checks, updates and exact local uninstall under the existing integrity and ownership rules. Updating SHALL preserve disabled state and SHALL NOT temporarily restore the standard discoverable entry.

#### Scenario: Upgrade a disabled installation
- **WHEN** the user confirms an eligible exact newer version for a disabled target
- **THEN** the shared core replaces its package recoverably with the disabled entry prepared before commit, updates only its release baseline and preserves disabled state, installation-directory ownership and sibling installations

#### Scenario: Uninstall a disabled installation offline
- **WHEN** the Registry is unavailable and the user confirms uninstall of a disabled installation with trusted local identity and baseline
- **THEN** its package files and exact record are removed through recoverable local deletion, its reservation is released, and other installations remain unchanged

#### Scenario: Plugin itself is disabled
- **WHEN** the SkillHub plugin is disabled or removed after a completed skill disablement
- **THEN** it does not silently restore all disabled packages or discard their persistent recovery and storage information

### Requirement: Explicit enablement protocol migration gate
Enablement SHALL NOT be encoded by silently extending the existing strict inventory v2 schema or introducing a competing writable state authority. A reviewed explicit inventory and journal protocol, compatible shared consumers, migration and rollback procedures, and historical-consumer verification SHALL precede enablement implementation and release.

#### Scenario: Read legacy inventory
- **WHEN** a compatible new consumer reads v1 or v2 inventory before a mutation
- **THEN** it projects logical enabled state without writing the source or inventing missing files or fingerprints; first-write migration preserves verified original bytes and unrelated records under compatible locks

#### Scenario: Historical CLI consumer
- **WHEN** a historical CLI reads or mutates a host with the proposed new inventory protocol
- **THEN** verification records whether each relevant command rejects before touching files; schema rejection alone SHALL NOT be accepted as proof of safe mixed-version operation, and incompatible writers must be stopped before migration

#### Scenario: Rollback with disabled packages
- **WHEN** an operator attempts to return to a consumer without disabled-installation support
- **THEN** the procedure requires stopped writers, verified package and destination state and a validated restoration or export path; restoring an old inventory backup alone SHALL NOT be reported as successful rollback

### Requirement: State-aware canonical package integrity
The shared core SHALL compute a disabled installation's canonical fingerprint by mapping only its exact recorded SKILL.md.disabled alias to SKILL.md, preserving bytes and all other package files. It SHALL NOT infer managed state from arbitrary disabled filenames. Publishing or export SHALL restore the canonical entry in the original or a validated export copy before producing a standard package.

#### Scenario: Two entries or alias collision
- **WHEN** both entry names exist, the alias is occupied by author content, either entry is an unsafe link, or the expected entry is absent
- **THEN** toggle and destructive lifecycle operations are blocked without overwriting either entry or normalizing arbitrary files

#### Scenario: Fingerprint remains canonical
- **WHEN** a trusted installation is disabled by the entry rename
- **THEN** the state-aware fingerprint equals the original canonical package fingerprint, and changing any entry bytes or resource bytes is still detected as local drift

#### Scenario: Export a disabled package
- **WHEN** export or publication uses a disabled installation
- **THEN** it first restores the canonical entry or constructs a separately validated canonical export copy; it does not publish the disabled directory verbatim

#### Scenario: Historical context and concurrent entry load
- **WHEN** a conversation already loaded the skill or reads the entry concurrently with a rename
- **THEN** resources retain their paths, loaded content is not claimed revoked, and a failed entry load is reported for fresh inspection without redirecting to another installation or claiming arbitrary external use has stopped

#### Scenario: Upgrade candidate already contains the alias
- **WHEN** a new package for a disabled installation already contains an author-supplied SKILL.md.disabled file
- **THEN** the disabled upgrade is blocked before replacement without overwriting that resource or changing the ordinary upload contract; alias normalization requires both trusted disabled inventory state and matching exact installation identity

#### Scenario: Exclusive entry transition interrupted between names
- **WHEN** an authorized transition has exclusively linked its destination but has not yet removed its source
- **THEN** recovery accepts the dual-entry state only with the matching journal phase, equal device/inode and verified canonical bytes; unrelated destination entries are preserved and block the transition

#### Scenario: Disabled discovery cannot be verified
- **WHEN** the trusted entry name cannot be safely parsed, the observation times out, or Host snapshot and definition disagree
- **THEN** discovery remains unknown independently of disabled installation state, and the installation never receives invocation authorization from this diagnostic


### Requirement: API-owned skill website links
The plugin SHALL use only the detail API's optional webUrl for the website action. It SHALL NOT construct a website route, fall back to the Registry address, or require a plugin website setting.

#### Scenario: Complete website URL provided
- **WHEN** the current source returns a safe absolute HTTP(S) webUrl for the matching detail
- **THEN** both detail entry points provide a new-tab link preserving that URL's page parameters, without appending local credentials or enabling opener access

#### Scenario: Website URL absent or unsafe
- **WHEN** webUrl is absent, null, blank, non-HTTP(S), credential-bearing or otherwise unsafe
- **THEN** the website action is hidden and the remaining detail and installation features remain available

#### Scenario: Server website identity
- **WHEN** the open-source server constructs the detail website URL
- **THEN** it uses only the configured trusted public website base and encoded skill coordinates, returns null without a valid base, and never trusts request Host or forwarding headers for this address


### Requirement: Use-first daily surfaces
Daily catalog, installed-skill groups, conversation inventory cards and default details SHALL foreground using a skill. Per-location maintenance SHALL remain available through secondary installation management, preserving exact location confirmation and existing lifecycle protections.

#### Scenario: Daily skill card with multiple installations
- **WHEN** a skill has multiple visible installation records
- **THEN** its daily card is grouped by source and coordinates, does not require a scope selection before the use attempt, and exposes per-location management separately

#### Scenario: Unique verified usable installation
- **WHEN** the user invokes skill-level use
- **THEN** the Host selects only a uniquely verified current-session usable managed installation, without ordering by scope, version or array position, and the existing new-session and draft-time checks still apply

#### Scenario: Ambiguous or unverifiable use selection
- **WHEN** several installations are usable, observations are incomplete, context changes, or selection times out
- **THEN** no installation is guessed or changed, no draft is sent, and the UI provides a concise blocked result with access to installation management

#### Scenario: Explicit installation management
- **WHEN** the user selects a particular installation in secondary management or uses an exact operation receipt
- **THEN** its exact reference remains authoritative for that request and is never silently replaced by the skill-level selector's winner


### Requirement: User-triggered installed update overview
The installed view SHALL check current managed installations only on explicit user action, aggregate results by skill, and retain exact installation identity for individual updates. It SHALL NOT execute updates during the scan.

#### Scenario: Mixed installation results
- **WHEN** installations of one skill return different update states
- **THEN** the view counts the skill once as upgradable if any location can upgrade, and the detail exposes each location independently
- **AND** blocked or unavailable results are not represented as current or executable upgrades

#### Scenario: Context or inventory changes during a scan
- **WHEN** the source, session, workspace, or installation reference changes
- **THEN** stale results cannot populate the current overview or authorize an update

#### Scenario: Retry and individual confirmation
- **WHEN** a user retries failed checks or opens an upgradable skill
- **THEN** checks remain read-only and the existing precise per-installation update confirmation is required before mutation

#### Scenario: Large inventory update checking
- **WHEN** a user checks many installations, including several locations of the same skill
- **THEN** a bounded Host-owned scan reads and indexes the trusted inventory once, validates only the requested target and inventory revision per result, and shares remote version resolution for the same source and skill within that scan
- **AND** inventory or context changes invalidate the scan, scan resources expire or are released on cancellation, and mutation preparation retains its independent full validation
