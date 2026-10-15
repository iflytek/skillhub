## ADDED Requirements

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
- **THEN** documentation and audit data match the fixed detail version, reviews are identified as skill-level, and read-only version history does not silently change the install version

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
- **WHEN** the user explicitly requests installation of an identified skill
- **THEN** the Host resolves a fixed version and trusted scope, starts a shared installation operation, and presents separate file and discovery outcomes

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
