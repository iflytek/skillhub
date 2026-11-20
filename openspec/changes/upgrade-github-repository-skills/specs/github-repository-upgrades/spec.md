## Purpose

通过本地 CLI 检查仓库来源技能的已安装版本和更新，并在保持原有文件、安装归属和权限边界的前提下升级。它帮助使用者维护独立技能，不将不同提交误判为损坏，不引入仓库安装账本，也不让网页推断本地状态。

## ADDED Requirements

### Requirement: REQ-1 Installed members only
The CLI SHALL match the repository's current accessible published plan against existing local per-skill inventory from the selected registry and target scope. It MUST NOT automatically install new members or delete local members absent from that plan.

#### Scenario: Newly added and removed members
- **WHEN** A is installed, B is a new upstream member, and a previously repository-installed C is absent from the current plan
- **THEN** A is checked, B is not installed, and C remains untouched with an unavailable/no-longer-listed explanation based on local provenance without guessing its hidden server state.

### Requirement: REQ-2 Version and content comparison
The CLI SHALL detect updates using exact version, content fingerprint and verified same-source release creation order for hash-based GitHub versions. Different upstream commit IDs alone MUST NOT generate an inconsistency warning. One invocation MUST reuse its resolved versions throughout execution.

#### Scenario: Mixed commits with unchanged content
- **WHEN** installed skills match the eligible versions and fingerprints but came from different commits
- **THEN** they are reported unchanged rather than inconsistent.

#### Scenario: Real GitHub hash versions and historical ordering evidence
- **WHEN** a later same-source version ID has a hash that sorts lower, or an old local record has no reliable ordering ID
- **THEN** the later known release remains upgradeable independently of hash order, and the unknown historical ordering is blocked without forced migration; exact matching version/fingerprint remains unchanged.

### Requirement: REQ-3 Protect local state and other owners
The CLI SHALL retain existing target locks, ownership, baseline checks, compare-and-swap inventory and rollback protections. Repository upgrades MUST NOT implicitly force local modifications or bypass Suite/shared ownership, disabled-state or source-identity guards.

#### Scenario: Local edits or another owner
- **WHEN** one target has local edits, is Suite-managed or cannot be safely maintained
- **THEN** that target is reported blocked and unchanged; independently safe targets can upgrade and the overall result is nonzero with explicit protected/not-attempted outcomes.

#### Scenario: Change after planning
- **WHEN** files, ownership or inventory change after the upgrade plan is built
- **THEN** execution rechecks the bound baseline and rejects unsafe replacement without reporting a false success.

### Requirement: REQ-4 Historical metadata compatibility
The CLI SHALL continue reading legacy per-skill inventory and metadata. Optional repository provenance MUST NOT act as an authority or fabricated file baseline. Missing provenance MUST NOT cause data deletion or a forced metadata migration.

#### Scenario: Existing direct installation without repository provenance
- **WHEN** a legacy/direct local Skill matches a current verified repository member and has a valid upgrade baseline
- **THEN** it can be checked normally without requiring a repository ledger; unsafe legacy paths or missing baselines remain blocked by existing protections.

### Requirement: REQ-5 Honest check and execution results
The CLI SHALL support a read-only check and report unchanged, upgradeable, blocked, failed and not-attempted outcomes. Unexpected partial failures MUST return nonzero and preserve completed successes with correct inventory. JSON stdout MUST remain one parseable result object.

#### Scenario: Download failure during upgrade
- **WHEN** an exact-version download fails after an earlier target upgraded
- **THEN** completed work remains correct, failed/unattempted work is explicit, and the command returns nonzero without claiming all-or-nothing rollback.

### Requirement: REQ-6 Web describes local operations accurately
The Web SHALL offer copyable repository check and upgrade commands without reporting local installed counts, local drift or executing filesystem changes. Install, current-member upgrade and complete snapshot ZIP MUST remain distinguishable.

#### Scenario: Web user chooses update check
- **WHEN** the user selects the check action on a repository page
- **THEN** the copied command performs a local read-only check, and the page explains that only CLI can compare local installations.
