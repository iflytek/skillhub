## ADDED Requirements

### Requirement: Source-aware installation status in search
The plugin SHALL enrich conversation search and plaza cards with read-only local installation status scoped to the selected Registry and trusted session user/project roots. Matching SHALL include Registry, namespace and slug. The result SHALL preserve installed versions, scopes and abnormal record states without exposing local paths or action tokens. This status SHALL NOT authorize mutation or assert DSH discovery, integrity or effective provider selection.

#### Scenario: Installed candidates across scopes
- **WHEN** a searched skill has matching user and project installation records
- **THEN** cards and model-visible results preserve each recorded version and scope while retaining the market candidate version independently, with release identity owned by each installed target and no inference that sibling targets share a version

#### Scenario: Foreign or untracked skills
- **WHEN** another Registry, namespace, manual copy or external provider supplies a same-name skill without a matching current-source managed record
- **THEN** it is not claimed as installed from the current source, and absence of a matching record is not presented as proof that DSH has no installed copy

#### Scenario: Abnormal and unavailable inventory
- **WHEN** a matching record is missing files or has inconsistent metadata, or any installation scope cannot be checked
- **THEN** the result distinguishes abnormal records and incomplete verification from a confirmed present record, and market browsing remains available

#### Scenario: Historical search snapshots
- **WHEN** a conversation search result is reopened after an installation change
- **THEN** its installation state remains explicitly labelled as a query-time snapshot and opening history sends no automatic inventory requests; older results without the optional state field remain readable with unknown status

#### Scenario: Context changes during a read
- **WHEN** the source, session, filesystem service or approved roots change while local status is being read
- **THEN** the stale read is rejected rather than attributed to the new context

### Requirement: Host-integrated skill plaza
The plugin SHALL provide one SkillHub entry with browse and installed-management tabs, a dedicated detail subpage with a top-left return action, and an install experience inside the DSH host while preserving workspace and conversation navigation. It SHALL support explicit update checks, upgrade plans and exact-target uninstall, without account or DSH-plugin-market screens.

#### Scenario: Open and return
- **WHEN** the user opens SkillHub and then returns to conversation
- **THEN** the prior conversation and workspace remain selected

#### Scenario: Accessible narrow layout
- **WHEN** the user operates by keyboard or in a narrow window
- **THEN** search, details, installation and return remain reachable, with visible focus and contextual loading, empty, error and success feedback

### Requirement: Self-contained same-repository package
The plugin SHALL be developed in the existing SkillHub repository as an independently installable native DSH package and SHALL NOT require a manual SkillHub CLI or Bun installation. The target surface SHALL be DSH Web on Linux, macOS and Windows, with release compatibility verified before claiming support.

#### Scenario: Clean environment
- **WHEN** the user installs the built package into a supported DSH environment without SkillHub CLI
- **THEN** the included Host and Client provide anonymous browsing and installation without relying on repository-local files

#### Scenario: Unverified host version
- **WHEN** a DSH release or platform has not passed compatibility checks
- **THEN** delivery documentation distinguishes that gap from verified support rather than treating source inspection as runtime proof

### Requirement: Configurable read-only detail modules
The plugin SHALL keep the details tab available and SHALL support per-Registry auto, show and hide configuration for versions, security audits and reviews. Display configuration SHALL NOT grant access or introduce login, review writing, scan triggering or automatic upgrades.

#### Scenario: SaaS reviews disabled
- **WHEN** reviews are configured as hide for the current instance
- **THEN** the review tab is absent and no review data or capability-probe request is sent

#### Scenario: Auto capability unknown
- **WHEN** auto mode has no reliable capability declaration
- **THEN** a bounded read-only probe may establish availability independently of tab activation, and temporary errors retain unknown rather than unsupported

#### Scenario: Anonymous audit denied
- **WHEN** the Registry denies anonymous audit access
- **THEN** auto hides that module and show explains the restriction without login or bypassing policy

#### Scenario: Empty supported module
- **WHEN** an available module returns an empty collection
- **THEN** its tab remains with an empty state and missing audit reports are not labelled safe

#### Scenario: Version context and review scope
- **WHEN** optional detail modules are displayed
- **THEN** documentation and audit data match the fixed detail version, reviews are identified as skill-level, and viewing version history does not silently change the install version; an explicit selection loads a new exact-version detail token

#### Scenario: Detail subpage return
- **WHEN** the user opens a skill from either catalog tab and uses the top-left return action
- **THEN** the original tab, filter, scroll position and focus are restored

### Requirement: Native package and transport lifecycle
The plugin SHALL use verified native bundle, Client module, main/sidebar and bundle-config mechanisms. Remote methods SHALL use the selected DSH release's generated and explicitly mounted Remote protocol rather than an implicit settings-message channel. Disabling the plugin SHALL dispose its UI and transport resources without deleting installed skills.

#### Scenario: Upgrade impact confirmation
- **WHEN** an upgrade plan affects more than one allowed installation target
- **THEN** the UI displays the old and new versions and every target before the user confirms

#### Scenario: Uninstall confirmation
- **WHEN** the user requests uninstall
- **THEN** the UI shows the exact identity, version, Host and target scope/path, defaults focus to cancel, and clearly states that remote skills and other installations remain

#### Scenario: Plugin disabled with operation active
- **WHEN** the plugin is disabled or its dependencies reload during a mutation
- **THEN** new writes stop, accepted work completes or is journaled for recovery, and Client listeners, polling, Remote contributions and slots are disposed according to the Host lifecycle


### Requirement: Conversation search and contextual detail
The plugin SHALL provide a read-only skill search tool with clickable cards in the existing DSH conversation. Opening a card SHALL display a contextual detail drawer, with a full-content-area overlay on narrow layouts, reusing the plaza detail modules and target selection without replacing the conversation.

#### Scenario: Search without installation intent
- **WHEN** the user asks to find a skill
- **THEN** the tool returns scoped search results and no skill package is installed

#### Scenario: Open and close conversation detail
- **WHEN** the user opens a search card and closes its detail drawer
- **THEN** the original conversation is preserved and focus returns to the triggering card, while optional tabs follow the same instance configuration as the plaza

### Requirement: Explicit conversational installation
The plugin SHALL provide an installation tool and a custom operation result card for explicit user installation requests. It SHALL use the same Host installation service, trusted targets, immutable version selection, conflict protection and operation journal as the plaza. It SHALL NOT accept arbitrary paths or download URLs from the model or automatically upgrade, uninstall or execute skills.

#### Scenario: Unambiguous explicit request
- **WHEN** the user explicitly requests installation of an identified skill and specifies current project or current user scope
- **THEN** the Host resolves a fixed version and trusted scope, starts a shared installation operation, and presents separate file and discovery outcomes

#### Scenario: Installation request without a specified scope
- **WHEN** the user explicitly requests installation of an unambiguously identified skill without specifying a scope
- **THEN** the Host resolves the user-level target and exact selected version without asking a scope question
- **AND** the model cannot select project scope on the user's behalf, and existing-target conflicts are not silently upgraded or overwritten

#### Scenario: Pending confirmation is canceled or becomes stale
- **WHEN** the user closes the confirmation or its source, conversation, project, Agent, runtime or Host changes
- **THEN** no installation is replayed and stale choices require a fresh search rather than coordinate or latest-version fallback
- **AND** an unchanged repeated call keeps the original pending version while changed arguments under that call are rejected
- **AND** pending history has no operation identity, cannot restore write authority and remains a query-time snapshot after a UI-confirmed installation

#### Scenario: Ambiguous identity or unavailable project
- **WHEN** the requested identity is ambiguous or the requested project has no trusted root
- **THEN** no installation starts and the user is asked to select the skill or a valid target without silently switching scope

#### Scenario: Stale source or fixed version
- **WHEN** a historical card has a different source or project context, or its fixed version is unavailable
- **THEN** installation is blocked pending refreshed selection rather than silently changing source, target or version

#### Scenario: Repeated or concurrent installation
- **WHEN** a tool call is retried or a page and conversation request the same target concurrently
- **THEN** stable request mapping and shared core locks prevent duplicate writes and preserve the original operation outcome

#### Scenario: Close or cancel waiting
- **WHEN** the drawer is closed or tool waiting ends after the Host accepted an installation
- **THEN** the operation remains queryable and the UI does not claim cancellation or rollback without evidence

#### Scenario: Historical result cannot be verified
- **WHEN** a conversation is reopened and the original Host operation record cannot be verified
- **THEN** the saved result is labelled historical and no write is replayed or current installation success assumed

#### Scenario: Install from a historical selection
- **WHEN** installation refers to a search card, detail or ordinal selection
- **THEN** the request carries a Host-issued selection reference binding the original context, and a missing or stale reference cannot be converted to a fresh coordinate-only installation


#### Scenario: Install a search candidate without opening details
- **WHEN** the user explicitly requests installation of a uniquely selected search candidate without opening its detail drawer
- **THEN** the Host validates the original reference and resolves the search-time exact version into an installation snapshot without invoking Client display acknowledgment, then uses the existing trusted installation transaction

#### Scenario: Search-time version is no longer latest
- **WHEN** a newer version is published after the search but before an unviewed candidate is installed
- **THEN** the Host installs only the exact version recorded by the search, or rejects an unavailable version; it never silently substitutes latest

#### Scenario: Context or displayed snapshot changes during verification
- **WHEN** source, session, project, reference validity or acknowledged snapshot changes while an unviewed candidate is being verified
- **THEN** the Host revalidates context and prioritizes a valid acknowledged snapshot, rejecting stale or ambiguous associations without falling back to coordinates

#### Scenario: Concurrent verification or failed verification
- **WHEN** requests verify the same unviewed candidate concurrently or a previous verification failed
- **THEN** concurrent verification is shared, failed verification does not authorize installation or mark the candidate viewed, and a subsequent valid request may retry verification

#### Scenario: Install after reading a detail drawer
- **WHEN** the user has viewed a fixed version in the drawer and subsequently requests installation using the original search reference
- **THEN** the Host resolves that reference to the acknowledged detail snapshot rather than resolving latest again, and rejects ambiguous version associations

#### Scenario: Tool returns acceptance before completion
- **WHEN** the installation tool returns before file installation finishes
- **THEN** it explicitly reports accepted with an operation reference, the result card queries the outcome, and neither the tool nor Agent claims installed or executed based on acceptance alone

#### Scenario: Historic operation context
- **WHEN** an installation card is restored under a different source or Host
- **THEN** it displays the recorded source, Host and timestamps as historical data without substituting current context or querying an unrelated operation

### Requirement: Independent validated plugin release
The plugin SHALL have its own package version, build validation and release workflow, separate from CLI, server-image and Chart releases. Each release SHALL identify supported DSH and Registry combinations and SHALL verify both the candidate artifact and the actual published package in a clean environment.

#### Scenario: Plugin-only release
- **WHEN** only the native plugin is released without a Registry API change
- **THEN** the release does not require a server redeployment and does not unintentionally trigger server-image, Chart or CLI publication

#### Scenario: Candidate and published artifacts
- **WHEN** a plugin version is prepared and then published
- **THEN** required exports and bundled files are validated and clean-environment checks confirm the published package matches the tested artifact without repository-local dependencies

#### Scenario: Unsupported compatibility combination
- **WHEN** a DSH version, operating system or Registry combination has not passed the relevant acceptance checks
- **THEN** release documentation labels it unverified rather than listing it as supported

### Requirement: Safe plugin rollback and recovery compatibility
Plugin release instructions SHALL define upgrade, disable and rollback procedures that preserve installed skills and local operation journals. Rollback SHALL NOT load a plugin version that cannot safely interpret outstanding operation state, and server rollback SHALL account for database migration compatibility.

#### Scenario: Plugin regression
- **WHEN** a plugin release must be disabled or rolled back
- **THEN** new writes stop, existing operation state is reconciled, and installed skill files and journals remain intact

#### Scenario: Incompatible operation journal
- **WHEN** a previous plugin version cannot interpret outstanding operation records
- **THEN** direct rollback to that version is blocked until recovery completes or a compatible repair version is used


### Requirement: Bounded historical operation results
Conversation history SHALL retain the original accepted result and operation context. Terminal installation status SHALL be read from the Host operation record while it is available; Client polling SHALL NOT imply permanent persistence of that terminal status in the transcript.

#### Scenario: Completed record has expired
- **WHEN** the retained Host operation record has expired but the conversation still contains its accepted result
- **THEN** the card reports a historical accepted request with an unverifiable outcome, without claiming successful installation or replaying the write


### Requirement: Explicit DSH version compatibility
The first plugin release SHALL target exactly one validated DSH release, with 0.2.0-rc.2 as the preferred M0 candidate rather than an already supported version. The plugin SHALL pin build dependencies and validate the selected release dependency set rather than independently consuming component latest tags. Prerelease versions SHALL be evaluated explicitly. Unverified or incompatible environments SHALL NOT accept new filesystem mutations.

#### Scenario: Different package distribution tags
- **WHEN** DSH component tags resolve to different release lines
- **THEN** the build uses a validated release dependency combination rather than mixing independent latest packages

#### Scenario: Host upgrade outside support
- **WHEN** DSH is upgraded beyond the plugin's validated compatibility set
- **THEN** new mutations are unavailable, existing journals remain recoverable, and available diagnostics explain the supported combinations

#### Scenario: Missing independent capability
- **WHEN** a nonessential UI contribution or project context is unavailable
- **THEN** the affected feature is disabled and the environment is labelled partially available, while only independently validated functionality remains enabled

### Requirement: Isolated compatibility adaptation and regression
Host-specific differences SHALL be confined to Host context, Remote and Client integration adapters. Installation business rules SHALL remain shared. Every combination declared fully supported SHALL pass both plaza and conversation acceptance, and support for additional DSH releases SHALL remain outside first-release delivery. Future exploratory checks SHALL NOT automatically expand published support.

#### Scenario: Add support for another release
- **WHEN** an additional DSH version is proposed for support
- **THEN** its package loading, protocol, tool UI, trusted targets, mutations and lifecycle pass the compatibility suite before dependency ranges and support documentation are expanded

#### Scenario: Baseline lacks a required capability
- **WHEN** the preferred M0 candidate cannot provide a required plugin capability through supported interfaces
- **THEN** the missing capability and implementation alternatives are evaluated, any replacement baseline is recorded and revalidated, and the plugin does not silently adopt Alpha dependencies or implement two first-release protocols

#### Scenario: Moving distribution tag
- **WHEN** the npm latest tag changes after the baseline is locked
- **THEN** builds and acceptance continue to use the locked exact release until an explicit baseline change is validated

### Requirement: Scoped modern components and explicit confirmation
The plugin SHALL adapt shadcn/Radix component source to DSH theme variables, use the Host React and React DOM runtime, and keep styles within its own namespace. Installation controls SHALL require confirmation of the skill, exact version and scope before submitting a new installation request.

#### Scenario: Host and another plugin share the page
- **WHEN** the SkillHub panel opens or closes alongside another plugin
- **THEN** its component selectors and reset rules do not alter the Host or the other plugin's typography, colors or layout

#### Scenario: Cancel an installation confirmation
- **WHEN** the user cancels or dismisses the confirmation dialog
- **THEN** no new installation request is sent and focus returns to the initiating control

#### Scenario: Confirm from conversation details
- **WHEN** the confirmation opens inside a conversation detail drawer
- **THEN** the dialog remains visible and keyboard accessible; dismissing it preserves the parent detail drawer and does not install a skill


### Requirement: Compact navigation and themed selection controls
The plugin SHALL use a compact toolbar with an accessible close action and discoverable current-source information. Dropdown controls SHALL use scoped shadcn/Radix Select components without changing trusted target, sorting or filtering semantics.

#### Scenario: Close the plaza
- **WHEN** the user activates the toolbar close action while a conversation is available
- **THEN** the plugin returns to that conversation through the Host layout mechanism without cancelling accepted operations or confusing the action with returning from details to the catalog

#### Scenario: Select a target inside conversation details
- **WHEN** the user opens the installation-scope dropdown inside a conversation detail drawer
- **THEN** options remain visible and keyboard accessible in the current theme, disabled targets cannot be selected, and dismissing the dropdown preserves the drawer and does not submit a write

### Requirement: Complete detail content cards
The detail tabs SHALL share a themed content card with a desktop content minimum height of 440 CSS pixels and a responsive narrow-screen layout. Loading, empty, denied and error states SHALL remain inside this structure. Version and review pagination SHALL sit below the content, and changing tabs SHALL update both content and active styling.

#### Scenario: A single review is available
- **WHEN** the source returns one review
- **THEN** the card displays its public author, rating, readable date and text without collapsing the content area, fabricating a version association or inventing an aggregate score

#### Scenario: Review data cannot be displayed
- **WHEN** reviews are empty, denied or fail to load
- **THEN** the card distinguishes those states and offers retry only where applicable; missing or invalid dates are labelled unknown rather than shown as invented timestamps

#### Scenario: Optional content becomes hidden
- **WHEN** a selected optional tab is hidden by source capability or configuration
- **THEN** selection returns to details and the hidden module is not requested


### Requirement: Explicit version selection without weakening target protection
The plaza SHALL allow users to select a downloadable version from version history, reload its exact detail and installation token, and confirm that version before installation. Existing target conflict and upgrade-plan protections SHALL remain effective.

#### Scenario: Select another published version
- **WHEN** a user explicitly selects a downloadable version
- **THEN** the detail and documentation are reloaded for that version and the previous installation control is unavailable while loading or after failure

#### Scenario: Select from a conversation detail
- **WHEN** the user wants a different version from a conversation-bound detail
- **THEN** the UI routes the user to the plaza through the existing validated navigation path rather than reusing the old conversation reference for the new version

### Requirement: Product-focused installation status and recoverable history
The UI SHALL distinguish current inventory from historical operations. Completed diagnostic details MAY be collapsed, but incomplete, failed, unknown, recovery-required or warning-bearing operations SHALL remain clearly visible and actionable.

#### Scenario: Historical success with changed inventory
- **WHEN** an old installation succeeded but current inventory is missing, different or unreadable
- **THEN** the primary status reflects the current inventory evidence or uncertainty, while the historical result remains accessible separately

#### Scenario: Completed clean upgrade history
- **WHEN** an upgrade is confirmed completed without warnings
- **THEN** the installed list remains the primary management view and the old operation is available in a collapsed history section without losing its stored result
### Requirement: Verified Cordis patch combinations within the DSH baseline

The plugin SHALL declare only explicitly validated Cordis versions for the selected DSH release. Runtime write checks SHALL verify the Host's actual resolved dependency manifests and reject unknown or incoherent dependency combinations. A retained baseline regression and a normal clean-install check SHALL remain independently observable.

#### Scenario: DSH transitive patch dependencies change
- **WHEN** a clean installation of the supported DSH release resolves the explicitly supported Cordis patch combination
- **THEN** browsing, installation, upgrade, uninstall and recovery checks use that verified Host combination without requiring an unrelated older peer copy
- **AND** the retained Cordis baseline continues to pass its separate regression

#### Scenario: Unknown or mixed Host dependencies
- **WHEN** any required Host dependency is missing, has an unverified version, belongs to an incoherent combination, or its current manifest differs from the resolved package metadata
- **THEN** new writes remain disabled
- **AND** existing operation queries remain available without replaying writes

### Requirement: Historical installation receipts recover current read-only context

After a Host restart, the plugin SHALL distinguish durable operation identity from ephemeral source revisions. Current inventory verification and installed-list navigation from a historical receipt SHALL use a freshly verified current source revision only after validating the original operation's Host and session ownership, matching receipt fields and unchanged Registry identity.

#### Scenario: Same Registry after Host restart
- **WHEN** the user reopens an installation receipt in the owning session after a Host restart and the configured Registry is unchanged
- **THEN** the original operation remains associated with its durable identity
- **AND** current inventory verification and installed-list navigation use the current source revision rather than the receipt's expired revision
- **AND** no install, upgrade, uninstall or recovery request is created

#### Scenario: Source or conversation changes during verification
- **WHEN** the configured Registry, current source revision, owning session, Host identity or trusted project context changes before a receipt read or navigation finishes
- **THEN** the stale result is discarded and navigation is rejected
- **AND** historical selection, detail, target and plan references are not renewed as write authorization

#### Scenario: The installed skill changed after the historical operation
- **WHEN** the historical operation completed but the skill was subsequently upgraded or uninstalled
- **THEN** the receipt preserves the original operation result while separately showing the current inventory
- **AND** it does not claim that the original version is still installed or submit a replacement installation


### Requirement: Compact market actions and readable cached details
The market SHALL use an independent whole-card detail trigger and sibling install-management buttons, omit ordinary no-source-record badges, and require a target when multiple installed scopes exist. Built-in labels SHALL have Chinese display names without changing filter slugs. Details SHALL omit complete leading Markdown frontmatter and global optional-module retry clutter. Public reads MAY be reused for 60 seconds using source/session/version identities; cached content SHALL remain visible during refresh and stale write references SHALL be revalidated.

#### Scenario: Card action does not open the detail page
- **WHEN** a user chooses remove or upgrade on a card
- **THEN** only the chosen installed target enters the existing preflight/confirmation flow
- **AND** the detail navigation trigger does not fire

#### Scenario: Return to a recently read detail
- **WHEN** the same session and source return within the read-cache window
- **THEN** the detail content and selected tab are reused without a blank loading replacement
- **AND** installation still uses valid Host context rather than trusting cached presentation


### Requirement: Direct market installation and safe native answers
Clicking a market install action SHALL open a compact confirmation dialog without navigating to the detail page. New installations SHALL default to current user. Project installation SHALL be available through advanced options and explicit user requests with a trusted project root. Existing installation management SHALL retain its actual scope rather than being silently redirected to the new-install default. A default selection SHALL NOT submit a page installation. When a native selection question is needed, it SHALL carry the exact live Agent and cancellation signal; skipped, custom, ambiguous or stale answers SHALL NOT authorize writes. Missing native providers SHALL retain a no-write confirmation-card fallback.

#### Scenario: Native scope question is canceled
- **WHEN** the user cancels or skips the question instead of selecting a supported scope
- **THEN** the tool returns cancelled without an operation identifier or filesystem mutation
- **AND** no automatic retry or default-scope installation occurs

#### Scenario: A card install is canceled
- **WHEN** the user opens the install dialog from a card and cancels it
- **THEN** no installation request is submitted and the catalog, scroll position and focused trigger remain available


### Requirement: Installation confirmation expands into compact progress
After an installation is explicitly confirmed and its original request is saved, the plugin SHALL keep the dialog open and expand it to the right where viewport space permits. The left column SHALL retain the skill, fixed version and selected scope. The right column SHALL show compact real operation progress and result without raw operation history. Narrow viewports SHALL stack the columns within the dialog. Closing SHALL NOT cancel an already submitted installation.

#### Scenario: Installation submission is waiting for a response
- **WHEN** the saved installation request is submitting and has no operation response yet
- **THEN** the right panel shows an indeterminate submitting state rather than an unknown-result warning
- **AND** expansion does not navigate or re-read skill details

#### Scenario: Operation completes and is read repeatedly
- **WHEN** the operation first enters a terminal phase
- **THEN** current-session installation projections refresh while existing cards remain visible
- **AND** repeated reads of the same terminal phase do not trigger repeated inventory refreshes
- **AND** only completed operations show full progress, with discovery and warning outcomes accurately distinguished


### Requirement: Current installed inventory owns installation management state
The plugin SHALL derive current installation state and removal actions from the current source/session inventory, separately from historical operation receipts. An existing installation SHALL display its version and an explicit management action in its selected scope without a duplicate installation action. Completed historical receipts SHALL NOT be displayed as current installation success after removal. Scope presentation SHALL distinguish project-only availability from current-user availability across projects without changing Host paths or permissions.

#### Scenario: User-level installation is opened in details
- **WHEN** a skill has a user-level record and no current-project record
- **THEN** details default to the user-level scope and display the installed version with a removal entry
- **AND** explicitly selecting the project scope offers a separate project installation without submitting it automatically

#### Scenario: A completed installation was later removed
- **WHEN** fresh inventory confirms that the selected installation is absent
- **THEN** the control shows an explicit installation action instead of the old success receipt
- **AND** new explicit confirmation may supersede the completed request while unresolved requests retain their original request identity


### Requirement: Removal opens a compact confirmation in place
Removal actions from the market, installed list and skill detail SHALL open confirmation in place rather than navigate to another detail screen. The confirmation SHALL show the selected skill, version, scope and scope-specific impact, while omitting routine raw Registry URLs, Host paths and plan expiry timestamps. Existing preflight, expiry, identity, fingerprint, confirmation and recovery protections SHALL remain unchanged.

#### Scenario: Open and cancel removal
- **WHEN** the user opens removal from an installed card
- **THEN** the original page remains underneath a confirmation dialog and the plugin performs read-only preflight
- **AND** confirmation remains disabled until the plan is ready
- **AND** cancellation or Escape performs no removal and restores focus to the originating trigger

#### Scenario: Confirm removal
- **WHEN** the user confirms a ready unexpired plan
- **THEN** the original idempotent request submits exactly once and progress/result remains in the same dialog
- **AND** inventory refreshes when the operation settles without navigating to detail
- **AND** failed, unknown or recovery-required results never appear as successful removal


### Requirement: Selecting a newer version offers an exact-version upgrade
Selecting a version SHALL be read-only. For an installed skill, a newer verifiable selected version SHALL offer an explicit upgrade to that exact version. The plugin SHALL preserve existing latest-version update methods while adding agent-scoped exact-version checks and preflight. Unavailable or mismatched selected candidates SHALL NOT fall back to latest. Same versions SHALL NOT offer duplicate upgrades; older or unorderable versions SHALL explain why upgrade is blocked.

#### Scenario: Upgrade to a selected non-latest version
- **WHEN** version 1.0 is installed, 1.2 is latest and the user selects 1.1
- **THEN** checking and confirmation bind 1.1 and no execution occurs until confirmation
- **AND** cancellation leaves files and inventory unchanged
- **AND** confirmed execution changes only the selected installation target, displays that scope, and preserves all sibling releases

#### Scenario: Local file changes or unmanaged targets block upgrade
- **WHEN** any required installed target cannot pass the existing baseline or ownership checks
- **THEN** confirmation is blocked for the entire upgrade and no target is overwritten
- **AND** current installed state remains independently queryable


### Requirement: Verified native skill-use handoff
The plugin SHALL offer a common “使用此技能” action on installed cards, installed or matching-version skill details, and completed installation surfaces. It SHALL revalidate the exact current-Registry installed record, selected scope, local file integrity and effective native DSH skill in the active session before inserting an editable invocation into the native composer. It SHALL preserve the existing draft, references and attachments and SHALL NOT submit messages, call a model, execute a skill, install files or change skill enablement. See use-skill-design.md for interaction and architecture.

#### Scenario: Installed market coordinate differs from native skill name
- **WHEN** the selected installed record is valid and its parsed native skill name differs from the market slug
- **THEN** the plugin inserts the verified native user invocation rather than guessing from the market slug
- **AND** focuses the target session composer, leaving sending to the user.

#### Scenario: Installed skill cannot be safely invoked
- **WHEN** files or inventory have changed, the native winner belongs to another location, the selected skill is not discovered, or user invocation is disabled
- **THEN** the action gives a concise actionable reason and leaves the draft and attachments untouched.

#### Scenario: Context or draft changes during verification
- **WHEN** the active session, workspace, source revision, composer or component lifetime changes, or native insertion comparison fails
- **THEN** the late result does not overwrite or submit any draft, including a newly selected session.

#### Scenario: Completed receipt is historical
- **WHEN** use is requested from an installation-success surface
- **THEN** the plugin rereads current inventory and matches source, coordinate, scope and version before preparing use
- **AND** a removed or upgraded historical target is not silently substituted with another installed version.


### Requirement: Versioned skill examples at one fixed package path
SkillHub SHALL recognize task examples only at `.skillhub/examples.json` relative to the skill root. Upload, download and installation SHALL preserve this single file and its bytes as versioned skill content. Examples SHALL follow the selected installed version. The feature SHALL NOT require editing SKILL.md, a separate Web configuration editor, an external examples cache or an independently refreshed copy. Detailed schema and cross-layer lifecycle rules SHALL be defined before implementation; see use-skill-design.md.

#### Scenario: An author uploads valid multiple examples
- **WHEN** a package contains protocol-valid `.skillhub/examples.json` with multiple task templates
- **THEN** the templates are associated with that exact skill version and remain at the same relative path after installation
- **AND** selecting use offers applicable templates and a custom-task entry without automatically sending a message.

#### Scenario: Example protocol is invalid or the file is absent
- **WHEN** the fixed file is absent or has invalid JSON or an invalid example structure
- **THEN** the skill remains usable through custom task entry without fabricated examples
- **AND** an existing invalid file is retained as ordinary versioned skill content with a non-blocking publication notice, subject to all normal package safety checks.

#### Scenario: A similarly named file or unsafe package is uploaded
- **WHEN** JSON is supplied at another path or package content violates general safety rules
- **THEN** other-path JSON is not interpreted as task examples and unsafe content follows existing rejection rules
- **AND** uploaded content never overrides or gains the authority of installer-internal `.skillhub/metadata.json`.

#### Scenario: Skill content passes through publishing and local lifecycle operations
- **WHEN** supported CLI, Web and DSH consumers publish, install, retry, sync, upgrade, remove or recover a skill containing the fixed file
- **THEN** package manifests, fingerprints, backups and directory checks consistently account for that file, including protocol-invalid ordinary content
- **AND** local modifications retain existing overwrite protection rather than being ignored as cache changes.

#### Scenario: Version or installation scope differs
- **WHEN** a user selects a different installed version, source or user/project installation
- **THEN** templates come from that exact selected installation, never from another version or scope
- **AND** existing native invocation, current-session and draft checks still apply.

#### Scenario: Legacy skills and consumers are evaluated
- **WHEN** an old skill has no examples file or an older CLI/plugin accesses a new-format installation
- **THEN** the old skill remains usable and consumer compatibility is explicitly tested and documented
- **AND** unsupported consumers are not claimed compatible merely because the current implementation is updated.


### Requirement: Localized packaged skill examples
The examples protocol SHALL support language-tagged arrays of objects with id, title and prompt, and an author-declared defaultLocale. Consumers SHALL select the host UI locale by exact tag, progressively less-specific parent tags, then the declared default. They SHALL NOT automatically translate, mix language collections or select an arbitrary first language. Canonically duplicate tags or a missing declared default collection SHALL be protocol-invalid, with ordinary-file fallback.

#### Scenario: Multiple languages are supplied
- **WHEN** a valid examples file provides Chinese, English or other supported language collections
- **THEN** the matching host-language collection is displayed in author order
- **AND** missing matches use the declared default while an explicitly empty matched collection remains empty.

#### Scenario: Locale changes while selecting an example
- **WHEN** the host language changes after a template selection is prepared
- **THEN** the old selection is invalidated rather than inserting a different localized item under the stale selection.


### Requirement: Structured examples and verified skill placeholder
Each example SHALL contain a stable id, display title and editable prompt. IDs SHALL be unique within each language collection; translations of the same logical example SHALL use the same id without requiring equal collection sizes. String-only entries SHALL be protocol-invalid. The consumer SHALL replace only the exact `{{skill}}` placeholder in prompt with the verified native skill invocation and SHALL NOT evaluate template expressions or infer the invocation from the market slug.

#### Scenario: A localized structured example is chosen
- **WHEN** the user confirms an example selected by language and id
- **THEN** the current file digest and installation are revalidated and the current matching prompt is used
- **AND** all exact skill placeholders are replaced with the verified invocation while title remains presentation text.

#### Scenario: Prompt has no supported skill placeholder
- **WHEN** the chosen prompt has no exact skill placeholder
- **THEN** the consumer supplies the verified invocation separately and preserves the chosen task text
- **AND** other template-like text remains literal, with no code evaluation or automatic sending.


### Requirement: Native conversation case selection by entry context
Catalog skill-use entries SHALL create and activate a new conversation in the same workspace. Conversation and tool-receipt entries SHALL remain in their current conversation. After target-session verification, task examples SHALL appear inline above the native composer rather than in a modal. The plugin SHALL use supported official session and slot APIs and SHALL NOT send messages or call models.

#### Scenario: Catalog use starts a separate task
- **WHEN** the user selects use from a skill-center installed card, matching detail or installation-success surface
- **THEN** exactly one new conversation is opened in the same workspace and task examples expand above its composer
- **AND** the original conversation draft, references, attachments and messages remain unchanged
- **AND** the selected source, installed version and scope are revalidated for the new conversation rather than replaced by latest or another installation.

#### Scenario: Conversation use preserves context
- **WHEN** use is selected from a conversation detail or tool installation receipt
- **THEN** the existing conversation remains active and its composer displays the inline case options
- **AND** case confirmation inserts editable text while preserving existing draft content and attachments.

#### Scenario: Cancel, missing examples and stale navigation
- **WHEN** the user cancels inline case selection
- **THEN** no text is inserted and no conversation or message is deleted
- **WHEN** no applicable case exists
- **THEN** no empty case picker appears and the verified custom-use handoff remains available
- **WHEN** session creation fails, or the source, workspace or active conversation changes before insertion
- **THEN** late work cannot overwrite the old draft or redirect the user back to a stale session.


#### Scenario: Native draft ownership survives the navigation
- **WHEN** catalog use would release the original conversation's last native view reference while its input has draft text, references, attachments or an unfinished input state
- **THEN** the plugin retains the original native session shell before navigation and preserves its full native input state
- **AND** it releases this temporary protection when the original main view resumes, the draft is cleared/sent, the session is removed or invalidated, or the plugin is disposed
- **AND** creation failure or cancellation before navigation releases the temporary reference without changing the draft
- **WHEN** eight previous nonempty drafts are already protected
- **THEN** another catalog-use action is refused before creating a conversation, with a concise instruction to revisit earlier drafts rather than evicting them
- **AND** this runtime protection does not promise attachment persistence across browser reload, shutdown or plugin disposal.

#### Scenario: 案例填入不产生额外首尾空行

- **WHEN** 用户确认案例或自定义任务并填入空的原生输入框
- **THEN** 插件不在正文前后额外添加换行，保留作者模板原有换行
- **AND** 插入已有草稿时只为紧邻且缺少换行的正文增加必要分隔，不重复已有换行
- **AND** 带引用、中文及 emoji 时仍使用原生捕获跨度和草稿版本，保留引用与附件，不自动发送

#### Scenario: Conversation detail hands off without blocking case selection
- **WHEN** the user selects use from an installed skill in a conversation search-detail modal and initial verification succeeds
- **THEN** the resident controller takes ownership before closing the detail modal and revealing inline cases
- **AND** unmounting the originating button does not cancel the handed-off choice, while session/source/CAS guards remain active
- **WHEN** initial verification fails
- **THEN** the detail remains open with an error
- **WHEN** the user cancels case selection
- **THEN** no draft is written and the detail is not reopened.

### Requirement: Conversation management of current-source installations
The plugin SHALL provide conversation tools and compact cards for listing current-source managed installations, checking latest or specified newer versions, requesting a single-target upgrade or removal, and querying owned operation outcomes. Tools SHALL use trusted session context and the shared lifecycle core, not shell commands. Model-provided confirmation booleans or arbitrary paths SHALL NOT authorize mutation.

#### Scenario: Multiple installations need selection
- **WHEN** a requested skill has multiple matching installed targets and the user has not specified which one
- **THEN** the Host presents the available installations with versions and scopes and waits for selection rather than applying the new-install default

#### Scenario: Mutation requires human confirmation
- **WHEN** the target and target release are known
- **THEN** the Host presents an input-adjacent confirmation or a no-write fallback card before any upgrade or removal starts
- **AND** even an explicit scope in model arguments cannot bypass human confirmation

#### Scenario: Compact operation result
- **WHEN** the user confirms a ready plan
- **THEN** its operation card shows actual progress and a concise outcome without full-page reload or mandatory detail navigation
- **AND** repeated submissions reuse the operation, and historical receipts do not assert present installation state

#### Scenario: Foreign or modified content
- **WHEN** a target is not attributable to the current source or has changed local contents
- **THEN** management refuses mutation with a relevant reason and does not offer a force action


#### Scenario: Accepted management operation does not need another approval
- **WHEN** a real human confirms an upgrade or removal and the Host returns accepted
- **THEN** model-visible guidance states approval is complete and the action is submitted, directs operation-status queries, and prohibits asking for another approval or submitting a duplicate mutation
- **AND** the Client continues to show the same operation rather than creating a new plan


#### Scenario: Distinct compact management views
- **WHEN** installed queries, update checks and mutation receipts appear in one conversation
- **THEN** only installed queries use a multi-item list; update checks show version comparison and the relevant update action; mutation and status receipts show compact identity, scope and outcome without duplicate action toolbars
- **AND** successful terminal receipts do not foreground status-query buttons

#### Scenario: Historical installation buttons become stale
- **WHEN** an installation reference no longer matches the current target or source
- **THEN** no mutation or skill insertion is automatically redirected to a replacement reference
- **AND** the Client replaces unusable actions with a clear explanation and an explicit read-only refresh entry, requiring a new deliberate action on refreshed records

- **AND** explicit refresh replaces the tool card's visible body with a clearly labelled current result, rather than nesting another installation list inside a historical row; stored transcript metadata remains unchanged


#### Scenario: Routine management results omit maintenance controls
- **WHEN** an installation list is readable or an operation has completed successfully without warnings
- **THEN** the view omits persistent refresh controls, technical detail disclosures, duplicate identities and repeated completion explanations
- **AND** installation rows use compact identity, version, scope and relevant actions without nested item cards; small viewports wrap content without clipping
- **WHEN** a historical reference is stale or a read is incomplete
- **THEN** the view offers a contextual read-only query retry without replaying any mutation
- **AND** warnings, recovery uncertainty and human confirmation remain visible when applicable


#### Scenario: Group installations without merging management targets
- **WHEN** current-source results contain multiple installations of one namespace and slug
- **THEN** the Client shows the skill identity once with a compact installation selector, displaying the selected target version and actions
- **AND** single installations show a scope label without an unnecessary selector; distinct targets sharing a scope are not dropped
- **AND** each action retains the exact installation reference; changing the visible selection cannot redirect in-flight responses or confirmation to another target

#### Scenario: Contextual controls in compact grouped cards
- **WHEN** a normal installation is usable
- **THEN** use is prominent and low-frequency check/update-removal management actions are available through a menu
- **WHEN** a specific update check or confirmation is shown
- **THEN** only relevant update or cancel/confirm actions appear, and confirmation locks its target
- **AND** successful and cancelled receipts do not add routine refresh or retry controls; failures expose only applicable recovery actions
- **AND** compact cards have a constrained desktop width and wrap on narrow screens without hiding target identity or scope


#### Scenario: Button changes precede a new model turn
- **WHEN** a UI action changes an installation after a tool result was recorded
- **THEN** stored tool history remains a snapshot, and the button does not fabricate or send a model message
- **AND** current installation answers use the freshly read official runtime-context snapshot or a fresh installed query, while new management mutations obtain current tool references rather than treating an old receipt as current state
- **AND** stale target references continue to be rejected by the Host, and user-facing explanations do not quote internal reference values or prefixes as evidence

#### Scenario: Multiple skill cards fit the conversation container
- **WHEN** multiple grouped skills are shown in a wide tool container
- **THEN** cards appear in at most two columns, each with its own installation selection and relevant actions
- **AND** narrow containers use one column and a single skill stays compact; opened management controls remain visible and operable within their card
