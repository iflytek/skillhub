# LibreChat SkillHub Hermes Integration PRD

## Status

Draft for review.

## Related Documents

- Product requirements: this document.
- Technical design: `docs/librechat-skillhub-hermes-design.md`.
- Implementation plan: `docs/librechat-skillhub-hermes-implementation-plan.md`.
- Existing SkillHub lifecycle authority: `docs/14-skill-lifecycle.md`.
- Existing SkillHub API conventions: `docs/06-api-design.md`.

When these documents disagree, the lifecycle document remains authoritative for SkillHub state transitions, and this PRD remains authoritative for product scope and user-facing behavior. Scanner findings, artifact fingerprint checks, and download readiness are safety overlays on top of lifecycle state; they must not be modeled as replacement lifecycle states in this integration.

## Background

LibreChat is the user-facing agent application, SkillHub is the enterprise skill registry, Control Tower is the policy and governance plane, and Hermes is the runtime that loads and invokes skills. Today these systems have overlapping skill concepts but no single governed flow for discovering, installing, publishing, and running organization skills.

This project makes SkillHub the registry of record for shared skills while exposing the registry through LibreChat and enforcing access with Control Tower. Hermes consumes only the pinned skills allowed for a user and session.

## Product Decisions

- SkillHub is authoritative for shared and published skill packages.
- LibreChat local skills remain private drafts and are not automatically migrated in V1.
- Control Tower is authoritative for integrated-deployment RBAC decisions.
- SkillHub lifecycle and package safety checks are still mandatory after Control Tower allows an action.
- Direct-published versions are not download-ready until SkillHub scanner/package policy allows distribution. If current SkillHub code publishes before scanner completion, integrated download, sync, and runtime paths must enforce the scanner overlay and block distribution until the verdict is acceptable.
- SkillHub is not directly reachable by end users in integrated deployment; user traffic goes through LibreChat.
- Installed skills are exact version pins with mandatory artifact fingerprints.
- Invalidated installed pins remain visible as disabled profile records until the user removes or updates them.
- Hermes must fail closed for a selected skill that cannot be revalidated, synced, or authorized.
- `skill.invoke` is the canonical runtime action.

## Goals

- Users can browse, inspect, and install allowed SkillHub skills from LibreChat.
- Installed skills are stored on the LibreChat user profile as exact SkillHub version pins.
- Hermes server workers sync pinned skills into isolated per-user profiles.
- Hermes invokes a skill only when Control Tower allows `skill.invoke` for the run envelope and invocation.
- Users can create or edit private skill drafts in LibreChat and submit them to SkillHub only after human confirmation.
- Admins can govern skill visibility, install, publish, review, manage, and runtime invocation through Control Tower.
- SkillHub continues to own package validation, scanner status, lifecycle, versioning, review workflow, artifact storage, and audit.

## Non-Goals

- V1 does not migrate existing LibreChat local skills into SkillHub automatically.
- V1 does not let Hermes publish directly to SkillHub without a LibreChat confirmation step.
- V1 does not make SkillHub a public end-user surface in the integrated deployment.
- V1 does not auto-update installed skills to latest.
- V1 does not replace all existing SkillHub standalone APIs.

## Personas

- End user: browses allowed registry skills and enables pinned versions for their profile.
- Developer or power user: drafts a skill and submits it for review or publish.
- Reviewer: evaluates submitted SkillHub versions and approves or rejects publication.
- Control Tower admin: defines who can view, install, publish, review, manage, and invoke skills.
- Operator: configures internal service credentials and rollout flags.

## User Stories

- As an end user, I can search the registry in LibreChat and see only skills I am allowed to view.
- As an end user, I can install an allowed skill version to my profile and see when a newer version exists.
- As an end user, I can remove an installed skill from my profile without deleting it from SkillHub.
- As a developer, I can import or author a private draft skill in LibreChat.
- As a developer, I can validate a draft against SkillHub package rules before submitting it.
- As a developer, I can confirm publication, submit to SkillHub, and track review status.
- As a reviewer, I can inspect pending skill submissions and approve or reject them when Control Tower allows `skill.review.decide`.
- As an admin, I can define policies for skill view, install, publish, direct publish, review, archive, visibility, yank, label, audit, and invoke.
- As an operator, I can enable this integration behind feature flags and move from report-only to enforced mode.

## Functional Requirements

| ID | Requirement | Owner | Acceptance Signal |
| --- | --- | --- | --- |
| FR-1 | Show only authorized and lifecycle-visible registry skills in LibreChat. | LibreChat, Control Tower, SkillHub | A denied skill is absent from search, detail, install, and run selection. |
| FR-2 | Install exact version pins to the LibreChat user profile. | LibreChat, SkillHub | Pin stores resource ID, version, and artifact fingerprint; latest changes do not mutate the pin. |
| FR-3 | Revalidate pins before sync and runtime use. | LibreChat, Hermes, SkillHub, Control Tower | Yanked, hidden, archived, rejected, scanner-blocked, deleted, or policy-revoked pins become disabled and cannot run. |
| FR-4 | Submit drafts through human-confirmed LibreChat publish flow. | LibreChat, SkillHub, Control Tower | Draft validation and publish submission require explicit confirmation and proper Control Tower action. |
| FR-5 | Enforce runtime invocation through Control Tower. | Control Tower, Hermes | Hermes denies a synced SkillHub skill present on disk when `skill.invoke` is absent from envelope or invocation authorization. |
| FR-6 | Prevent direct integrated-deployment SkillHub bypass. | Operators, SkillHub | Raw user-facing SkillHub access is internal-only or rejects protected calls without service authentication and signed Control Tower context. |
| FR-7 | Preserve standalone SkillHub behavior outside integrated deployment. | SkillHub | Existing public, CLI, search, publish, review, download, and governance flows continue when integrated mode is disabled. |

### Registry Discovery

- LibreChat provides a Skill Registry surface backed by SkillHub.
- Registry list, search, detail, version history, and update availability use `SkillHub lifecycle visibility ∩ Control Tower allow`.
- End-user discovery and install surfaces expose only `PUBLISHED`, non-hidden, non-archived, non-yanked, scanner-acceptable versions.
- Draft, pending review, rejected, yanked, and scanner-blocked versions are visible only through explicit owner, review, or admin surfaces.
- Download-ready means an exact `PUBLISHED` version whose container is active and not hidden, whose artifact fingerprint is present, and whose scanner/package policy outcome allows distribution.
- Scanner-acceptable means SkillHub scanner policy permits distribution. Pending, failed, expired, or policy-blocked scanner outcomes are scanner-blocked and cannot be installed, synced, or invoked.
- Raw SkillHub user APIs are not externally routable in integrated deployment. If an operator exposes any SkillHub route externally, protected registry calls must require signed Control Tower decision context.
- Registry skill identifiers use these resource IDs:
  - Namespace: `skillhub:@namespace`
  - Skill container: `skillhub:@namespace/slug`
  - Skill version: `skillhub:@namespace/slug@version`
  - Review task: `skillhub:review-task:{reviewTaskPublicId}`

### Installation And Profile Pins

- A user installs an exact SkillHub version to their LibreChat user profile.
- Installed profile records include resource ID, namespace, slug, version, artifact fingerprint, display metadata, install timestamp, and last sync status.
- Artifact fingerprint is canonical `sha256:{64 lowercase hex}` over the exact SkillHub stored bundle bytes returned by the download endpoint. SkillHub computes it after package validation and before transport; LibreChat stores it unchanged; Hermes recomputes it on the downloaded bytes before extraction or load.
- Installing requires Control Tower approval for `skill.install` on the version resource.
- Downloads for installation use SkillHub lifecycle checks and exact version resolution.
- Updates are explicit. LibreChat may show a newer published version, but does not change the pin automatically.
- Disabled pins remain visible in the profile with the reason and next safe action.

### Pin Revalidation

- LibreChat resolves installed pins server-side from the authenticated user profile; browser-supplied skill IDs are never trusted for run requests.
- Before Hermes sync or run dispatch, LibreChat and Hermes revalidate each selected pin against current SkillHub lifecycle and Control Tower policy.
- Only exact, published, download-ready versions with matching artifact fingerprints can be synced or invoked.
- If a previously installed version becomes yanked, hidden, archived, deleted, rejected, scanner-blocked, or policy-revoked, LibreChat marks the pin disabled and Hermes must not load or invoke it.
- If a user starts a run with a disabled selected skill, the run fails closed with a clear denial rather than silently dropping the skill.

### Runtime Use

- LibreChat includes pinned skill version resource IDs in the governed Hermes run request.
- Control Tower compiles allowed `skill.invoke` resources into the capability envelope.
- Hermes syncs pinned skill bundles into a per-user server-worker profile.
- Hermes calls Control Tower per-invocation authorization before invoking a skill.
- A skill present on disk is not sufficient for use if it is absent from the envelope or invocation authorization fails.
- LibreChat private drafts are authoring-only in this V1 integration. Existing non-SkillHub local execution paths, if present in a deployment, are outside this registry contract and must not be represented as SkillHub pins or used to bypass SkillHub runtime authorization.

### Draft And Publish

- LibreChat local skills are treated as private drafts in V1.
- A draft can be validated against SkillHub package policy before submission.
- Submitting a draft requires a human confirmation screen in LibreChat.
- Publication requires Control Tower permission:
  - `skill.publish` for normal submission or review flow.
  - `skill.publish.direct` for direct publish when SkillHub lifecycle permits it.
- `skill.publish.direct` is a trusted integration capability, not a blanket SkillHub `SUPER_ADMIN` role. SkillHub still checks namespace status, package validity, scanner state, warning confirmation, version constraints, and audit actor mapping.
- Publish authorization is resource-targeted: creating a new skill requires a namespace-scoped token for `skillhub:@namespace`; publishing a new version of an existing skill requires a container-scoped token for `skillhub:@namespace/slug`; the token resource must match the namespace and slug derived from the package and request body.
- Direct-published versions may become `PUBLISHED` according to lifecycle, but they are not distributable through integrated resolve/download/sync/runtime endpoints until scanner/package policy marks the exact artifact download-ready.
- SkillHub remains responsible for scanner findings, warning confirmation, review tasks, version lifecycle, and audit events.

### Admin And Review

- LibreChat exposes admin and review surfaces for users authorized by Control Tower.
- Approve and reject decisions require `skill.review.decide`.
- Submitter-owned review withdrawal requires `skill.review.withdraw`, is valid only for a pending review submitted by the same Control Tower subject, and returns the version to the SkillHub lifecycle `DRAFT` state. Current SkillHub code paths that return withdrawn versions to `UPLOADED` must be migrated before V1 enforcement.
- Lifecycle management actions use operation-specific Control Tower actions:
  - `skill.archive` for owner or namespace-admin archive and unarchive.
  - `skill.visibility.manage` for platform-governed hide and restore.
  - `skill.yank` for platform-governed yank of a published version.
  - `skill.label.attach` for attaching or detaching existing non-privileged labels on skill containers or versions.
  - `skill.label.definition.manage` for creating, editing, deleting, or applying privileged/global label definitions.
- Admin UI never trusts client-side visibility alone; backend adapters recheck Control Tower decisions.

## Action And Resource Matrix

| Action | Resource Scope | Purpose | Extra SkillHub Enforcement |
| --- | --- | --- | --- |
| `skill.view` | Namespace, container, version | Discover and inspect allowed skills. | Lifecycle visibility and hidden/archive rules. |
| `skill.install` | Version | Install or download exact published version to profile. | Published-only, scanner-acceptable, artifact fingerprint required. |
| `skill.invoke` | Version | Invoke pinned version in Hermes runtime. | Envelope membership, invocation authorization, current lifecycle revalidation. |
| `skill.publish` | Namespace, container | Submit draft to normal publish/review flow. | Namespace active, package policy, review lifecycle, warning confirmation. |
| `skill.publish.direct` | Namespace, container | Direct publish without review where policy and lifecycle allow. | Same as publish plus direct-publish eligibility. |
| `skill.review.decide` | Review task | Approve or reject pending review. | Does not include submitter withdrawal. |
| `skill.review.withdraw` | Review task | Submitter withdraws pending review. | Actor must be the original submitter; review task must be pending; lifecycle target is `DRAFT`. |
| `skill.archive` | Container | Archive or unarchive a skill. | Actor must satisfy owner or namespace-admin lifecycle boundary. |
| `skill.visibility.manage` | Container | Hide or restore a skill. | Platform governance only; namespace admin alone is insufficient. |
| `skill.yank` | Version | Yank a published version. | Platform governance only; recalculates latest published pointer. |
| `skill.label.attach` | Container, version | Attach or detach existing non-privileged labels. | Label exists, actor may edit the target, and privileged labels are rejected. |
| `skill.label.definition.manage` | Namespace, container, version | Manage global, privileged, or governed label definitions and assignments. | SkillHub platform governance rules still apply. |
| `skill.audit.read` | Namespace, container, version, review task | Read audit records for skill governance. | Audit redaction and platform policy. |

## Security And Compliance Requirements

- LibreChat remains the login and session owner.
- Control Tower maps LibreChat external users to subjects and resolves groups and roles server-side.
- SkillHub validates signed Control Tower decision context for protected internal integration calls.
- Signed decision context includes issuer, audience, key ID, actor subject, external user ID, workspace or tenant context, action, canonical resource ID, policy version, HTTP method, route, expiry, nonce, request body hash for every protected endpoint that carries a body, and asymmetric signature. Control Tower owns private signing keys; SkillHub receives only public verification metadata.
- Protected SkillHub integration endpoints require both trusted service authentication and signed Control Tower decision context, except service-auth-only capability export and package validation endpoints explicitly listed in the design.
- Browser-originated requests to protected SkillHub integration endpoints fail even if they attempt to send Control Tower decision headers.
- SkillHub does not trust browser-supplied role, group, namespace, or decision headers.
- SkillHub records audit events with LibreChat external user and Control Tower subject where available.
- Service secrets and decision tokens are never logged.
- Downloads and publish uploads continue to use SkillHub package, path, file size, lifecycle, and scanner protections.
- Decision tokens are single-use for protected downloads and mutating calls, and nonce replay is checked for every protected decision token for the configured token TTL.
- Report-only mode may record visibility diagnostics only and must not allow install, publish, review, download, sync, or runtime operations that would fail in enforced mode.
- Report-only decisions that would deny in enforced mode must not mint SkillHub decision tokens and must not mutate pins, drafts, downloads, review state, publish state, synced bundles, or runtime invocation state.

## Rollout Requirements

- Feature flags gate LibreChat registry UI, LibreChat profile pins, LibreChat runtime skill requests, SkillHub trusted Control Tower integration, Hermes SkillHub sync, and Hermes `skill.invoke` enforcement.
- All integration flags default off.
- Initial rollout supports report-only Control Tower decisions for visibility diagnostics only.
- Enforcement order:
  1. Registry visibility report-only diagnostics.
  2. Registry install and download enforcement.
  3. Publish and review enforcement.
  4. Hermes runtime invocation enforcement.
- Hard gate: `LIBRECHAT_SKILLHUB_RUNTIME_REQUESTS` must stay disabled until the same deployed environment has `HERMES_SKILL_INVOKE_ENFORCEMENT` enabled, Hermes sync is using LibreChat-mediated downloads, and `skill.invoke` denial tests pass for skills present on disk but absent from the envelope or invocation authorization.
- Rollback disables the LibreChat registry UI, profile pin write paths, Hermes SkillHub sync, and runtime SkillHub skill loading while preserving existing SkillHub standalone behavior.
- Rollback leaves existing pins, Control Tower inventory, and Hermes bundle metadata inert but readable for diagnostics.

## Failure Matrix

| Condition | Expected Behavior | User Result | Audit/Metric |
| --- | --- | --- | --- |
| Control Tower unavailable in enforced mode | Fail closed for protected operations. | Registry action denied with retriable message. | Policy dependency failure metric. |
| SkillHub unavailable | Do not mutate pins or drafts. | Registry action shows retriable error. | SkillHub dependency failure metric. |
| Decision token missing, expired, tampered, mismatched, or replayed | SkillHub rejects request. | Forbidden or unauthorized operation. | Security audit without token contents. |
| Protected SkillHub call has decision token but lacks trusted service auth | SkillHub rejects request before domain workflow. | Forbidden or unauthorized operation. | Security audit with service principal result only. |
| Pinned version yanked, hidden, archived, deleted, rejected, scanner-blocked, or policy-revoked | Pin disabled; Hermes cannot sync or invoke. | Profile shows disabled pin and reason. | Pin revalidation failure metric. |
| Artifact fingerprint mismatch | Hermes discards bundle and fails closed. | Run denied for the selected skill. | Artifact integrity audit. |
| Runtime invocation missing envelope authorization | Hermes refuses invocation. | Run denied for selected skill. | Invocation denied metric. |

## Acceptance Criteria

- Allowed users can discover, install, sync, and invoke an allowed published skill version.
- Denied users cannot see, install, download, or invoke disallowed skills through LibreChat and Hermes.
- Direct raw SkillHub user calls are unavailable or rejected for protected registry operations in integrated deployment.
- Installed skill pins remain stable when SkillHub publishes a newer version.
- Installed skill pins include mandatory artifact fingerprints.
- A previously installed version that is yanked, hidden, archived, deleted, rejected, scanner-blocked, or policy-revoked becomes disabled and cannot be synced or invoked.
- Skill draft publishing requires explicit human confirmation.
- Normal draft submission creates a pending SkillHub review task; approve publishes, reject retains a rejected version, and submitter withdrawal returns the version to `DRAFT`.
- Direct publish requires `skill.publish.direct`, preserves latest published pointer semantics, and still applies package, scanner, warning-confirmation, namespace, and audit checks.
- Direct-published versions are not installable, downloadable, syncable, or invokable until scanner/package policy marks the exact artifact download-ready.
- SkillHub rejects protected integration calls with missing, expired, tampered, mismatched, or replayed decision context.
- SkillHub rejects protected integration calls that have a decision token but lack trusted service authentication or use a browser-originated caller.
- SkillHub rejects decision tokens with wrong audience, method, path, body hash, workspace, action, or resource.
- Control Tower envelopes and invocation authorization use `skill.invoke`.
- Admin and review actions are unavailable without Control Tower permission.
- `skill.review.decide` covers approve/reject only; submitter withdrawal uses `skill.review.withdraw`.
- Raw SkillHub end-user access is not part of the integrated deployment exposure.
- Existing standalone SkillHub public, CLI, search, publish, review, download, and governance smoke flows continue to pass when `SKILLHUB_INTEGRATED_MODE` is disabled.
- LibreChat does not send `requested.skills` until the implementation-plan runtime flag gate has passed in the target environment.
- Report-only mode mints no SkillHub decision tokens for would-deny checks and causes no pin, draft, download, review, governance, sync, or runtime mutation.

## Open Questions

- Whether a future version should migrate existing LibreChat local skills to SkillHub.
- Whether workspace-owned skill pins should be added after per-user profile pins are stable.
- Whether SkillHub should expose optional direct SSO access for administrators in this integrated deployment.
