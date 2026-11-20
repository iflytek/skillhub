## Purpose

按 GitHub 来源仓库批量安装当前可用技能，保留技能各自的审核、版本和权限边界；安装计划固定本次操作的内容，并向使用者准确说明排除项、成功和失败，不把仓库转成 Suite 或宣称其内容来自同一提交。

## ADDED Requirements

### Requirement: REQ-1 Current available repository plan
The system SHALL resolve a complete, bounded read-only installation plan for a stable repository ID using each accessible member's current published, download-ready, non-yanked version. It MUST NOT use a paginated catalog page or a complete historical snapshot as this plan.

#### Scenario: Mixed commits and a newer pending revision
- **WHEN** members A and B have usable published versions from different commits and A also has a newer pending revision
- **THEN** the plan contains A's current usable published version and B's usable published version, regardless of the latest snapshot's pending state.

#### Scenario: More members than a catalog page
- **WHEN** a repository has 130 eligible members
- **THEN** the plan contains all 130 exactly once in deterministic order, regardless of the current page and search filter.

#### Scenario: Oversized or empty plan
- **WHEN** the eligible member count exceeds the documented bound or no eligible members remain
- **THEN** the request fails explicitly without returning a truncated installation plan or writing local files.

### Requirement: REQ-2 Exact identities and continuing authorization
The system SHALL pin each member's skill ID, version ID, version string and fingerprint for one invocation. Downloads MUST continue to enforce current authorization and availability. Anonymous public reads and Token reads with skill:read MUST obey the same visibility policy as the catalog.

#### Scenario: Publication during installation
- **WHEN** a newer version becomes published after plan resolution
- **THEN** the invocation installs only its previously resolved exact versions; the newer publication is considered next time.

#### Scenario: Permission or publication withdrawn
- **WHEN** a member becomes inaccessible, hidden or yanked after the plan is resolved
- **THEN** its download fails without substituting another version or bypassing download authorization.

#### Scenario: Restricted or not yet published members
- **WHEN** a repository contains hidden, inaccessible or first-publication-pending skills
- **THEN** they are excluded from the installable plan; the UI explains that only accessible published members are included without exposing excluded names, private status or counts.

### Requirement: REQ-3 Reuse per-skill safe installation
The CLI SHALL install the resolved members with existing per-skill target locks, fingerprint validation, ownership checks and metadata. It MUST NOT create a Suite or a repository installation ledger, implicitly overwrite local changes, rename conflicting skills, or delete unrelated files.

#### Scenario: Conflict or shared ownership
- **WHEN** a destination contains an unrelated same-slug skill, a locally changed skill, a disabled installation or a Suite-managed target
- **THEN** the existing safety checks reject unsafe replacement and other members can report their independent results.

#### Scenario: Concurrent installation
- **WHEN** another process modifies a selected target or inventory during installation
- **THEN** existing locking and consistency checks preserve the other operation and prevent false successful inventory records.

### Requirement: REQ-4 Honest batch results
The CLI SHALL report installed, skipped and failed member outcomes separately and keep JSON stdout parseable. An unexpected partial failure or zero installed members MUST return a nonzero exit code. Planned exclusions MUST be explained without reporting them as successful installations. Known indeterminate commits or incomplete rollbacks MUST preserve safe recovery details and stop further writes.

#### Scenario: One member fails after another succeeds
- **WHEN** A installs and B's download or target validation fails
- **THEN** A remains installed with correct metadata, B has no false success record, output identifies both results safely and the process exits nonzero.

### Requirement: REQ-5 Web installation entry and snapshot separation
The repository detail SHALL provide an installation panel with eligible versions, a copyable CLI command and loading, empty and error states. It MUST distinguish current-member installation from the existing complete snapshot ZIP and MUST NOT claim to know local installation state.

#### Scenario: Pending latest complete snapshot
- **WHEN** the latest snapshot is pending but current members have downloadable published versions
- **THEN** the installation panel remains usable and explains the per-skill version selection; snapshot download eligibility remains unchanged.
