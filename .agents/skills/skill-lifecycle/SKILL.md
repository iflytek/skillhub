---
name: skill-lifecycle
description: The authoritative skill lifecycle state model including container states, version states, review workflow states, visibility overlay, and governance actions. Ensures agents don't introduce invalid states or transitions.
license: Apache-2.0
---

# Skill Lifecycle Skill

## Trigger

Use this skill when:
- Modifying skill publish, review, or unpublish flows
- Adding or changing skill/version status fields
- Working on search, detail pages, or listing pages that show skill state
- Implementing governance actions (hide, yank, archive)
- Adding new state transitions or permission checks

## State Model

### Skill Container States

Enum `SkillStatus` (`domain/skill/SkillStatus.java`):

| Value | Meaning |
|-------|---------|
| `ACTIVE` | Skill is operational and can have versions published |
| `HIDDEN` | Skill hidden by platform governance (design doc says prefer boolean `hidden` flag instead) |
| `ARCHIVED` | Skill archived by owner/namespace admin, cannot publish new versions |

**Design-vs-code note**: `docs/14-skill-lifecycle.md` specifies `hidden` should be a governance
overlay (boolean flag) rather than a lifecycle enum state. The current code still defines
`SkillStatus.HIDDEN`. New code should use the `skill.hidden` boolean field, not the enum value.

### SkillVersion States

Enum `SkillVersionStatus` (`domain/skill/SkillVersionStatus.java`):

| Value | Meaning |
|-------|---------|
| `DRAFT` | Non-public draft, can resubmit or delete |
| `SCANNING` | Undergoing security scan |
| `SCAN_FAILED` | Security scan failed |
| `UPLOADED` | Uploaded but not yet submitted for review (or withdrawn from review) |
| `PENDING_REVIEW` | Frozen pending reviewer action |
| `PUBLISHED` | Currently distributable |
| `REJECTED` | Review denied, retained |
| `YANKED` | Was published, withdrawn from distribution |

### ReviewTask States

Enum `ReviewTaskStatus` (`domain/review/ReviewTaskStatus.java`):

| Value | Meaning |
|-------|---------|
| `PENDING` | Awaiting reviewer |
| `APPROVED` | Reviewer approved |
| `REJECTED` | Reviewer rejected |

### Visibility Model

Enum `SkillVisibility` (used in `SkillPublishService`):

| Value | Publish Path |
|-------|-------------|
| `PUBLIC` | Creates `PENDING_REVIEW` version, review task, security scan |
| `NAMESPACE_ONLY` | Same as PUBLIC but limited visibility scope |
| `PRIVATE` | Goes directly to `UPLOADED` status, no review task |

`SUPER_ADMIN` role bypasses review — versions go directly to `PUBLISHED`.

### Latest Version Pointer

`Skill.latestVersionId` is **only** the latest published pointer:
- Can only point to a `PUBLISHED` version
- May be `null` if no published version exists
- `latest` tag auto-follows this pointer (read-only)
- When yanking: recalculates to newest remaining `PUBLISHED` version, or `null`

### Key Transitions

| Action | From | To | Notes | Source |
|--------|------|-----|-------|--------|
| First upload (PUBLIC/NAMESPACE_ONLY) | — | `PENDING_REVIEW` | Review task created | `SkillPublishService` |
| First upload (SUPER_ADMIN) | — | `PUBLISHED` | Direct publish, `SkillPublishedEvent` emitted | `SkillPublishService` |
| First upload (PRIVATE) | — | `UPLOADED` | No review task, `latestVersionId` updated | `SkillPublishService` |
| Review approve | `PENDING_REVIEW` | `PUBLISHED` | Updates `latestVersionId` | Review workflow |
| Review reject | `PENDING_REVIEW` | `REJECTED` | Version retained | Review workflow |
| Withdraw review | `PENDING_REVIEW` | `UPLOADED` | Deletes pending `ReviewTask` | `SkillGovernanceService.withdrawPendingVersion` |
| Yank | `PUBLISHED` | `YANKED` | Recalculates `latestVersionId` | `SkillGovernanceService.yankVersion` |
| Hide | — | `hidden=true` | Independent overlay | `SkillGovernanceService.hideSkill` |
| Restore | — | `hidden=false` | Independent overlay | `SkillGovernanceService.unhideSkill` |
| Archive | `ACTIVE` | `ARCHIVED` | `SkillStatusChangedEvent` emitted | `SkillGovernanceService.archiveSkill` |
| Unarchive | `ARCHIVED` | `ACTIVE` | `SkillStatusChangedEvent` emitted | `SkillGovernanceService.unarchiveSkill` |
| New publish (existing pending) | `PENDING_REVIEW` | `UPLOADED` | Auto-withdraw + delete review task | `SkillPublishService` |
| Delete version | `DRAFT`/`REJECTED`/`SCAN_FAILED`/`UPLOADED` | — | Last version protected | `SkillGovernanceService.deleteVersion` |

### Yank Pointer Recalculation

When yanking the current `latestVersionId` (`SkillGovernanceService`):
1. Query all remaining `PUBLISHED` versions for the skill
2. Sort by `publishedAt` DESC, then `createdAt` DESC, then `id` DESC
3. Point `latestVersionId` to the top result, or `null` if none remain

### Lifecycle Projection

Read models (detail, my-skills, favorites, search) use `*QueryRepository` patterns:
- `headlineVersion` — Main display version for the page
- `publishedVersion` — Latest published version
- `ownerPreviewVersion` — Pending review version (visible to owner/namespace admin)
- `resolutionMode` — `PUBLISHED`, `OWNER_PREVIEW`, or `NONE`

**Public browsing, install, download, search only use `publishedVersion`.**

### Permission Boundaries

| Action | Who |
|--------|-----|
| Withdraw review | Submitter only |
| Delete version | Owner or namespace admin, only `DRAFT`/`REJECTED`/`SCAN_FAILED`/`UPLOADED` |
| Archive/unarchive | Owner or namespace admin (`ADMIN` or `OWNER` role) |
| Hide/restore | Platform governance (no permission check in code) |
| Yank | Platform governance (no permission check in code) |
| Publish PUBLIC skill | Namespace member (or `SUPER_ADMIN`) |
| Publish PRIVATE skill | Namespace member (or `SUPER_ADMIN`) |

### Delete Version Constraints

`SkillGovernanceService.deleteVersion` enforces:
- Only `DRAFT`, `REJECTED`, `SCAN_FAILED`, or `UPLOADED` versions can be deleted
- Cannot delete the last remaining version of a skill
- Deletes associated storage keys (individual files + `bundle.zip`)
- Deletes associated security scan records
- Updates `latestVersionId` if the deleted version was the pointer
- Storage deletion happens after transaction commit with compensation recording

### Domain Events

| Event | When Emitted |
|-------|-------------|
| `SkillStatusChangedEvent` | Archive or unarchive |
| `SkillPublishedEvent` | SUPER_ADMIN direct publish |
| `SkillVersionYankedEvent` | Yank action |
| `ReviewSubmittedEvent` | Create review task for PUBLIC/NAMESPACE_ONLY |

### Common Pitfalls

- Setting `SkillStatus.HIDDEN` directly — use `skill.setHidden(true)` via `SkillGovernanceService` instead
- Forgetting to recalculate `latestVersionId` after yank or version deletion
- Not auto-withdrawing pending versions when publishing a new version
- Missing the `confirmWarnings` two-step publish flow (warnings require explicit confirmation)
- Assuming all publish flows create review tasks — `PRIVATE` visibility skips review
