# Design

## Product boundary

One permission belongs to one ordinary MEMBER in one ACTIVE TEAM namespace.
Only that namespace's OWNER/ADMIN can grant or revoke it through the existing session/CSRF management surface.
No namespace switch, new role, token scope or standalone management page is introduced.
The grant applies equally to browser submissions and tokens already authorized to submit.
It grants no review, delete, ownership, management, PRIVATE confirmation or cross-namespace authority.

## Data model and migration

Rewrite unreleased V123 only. Remove the draft namespace flag and review-exempt boolean model.
Use namespace_member.team_auto_publish_grant_id (nullable UUID). NULL is the default, including old members.
Generate a fresh UUID only when transitioning from ungranted to granted; repeated enable is idempotent.
Revocation clears the active identity. A later grant always has a different identity, including after member rejoining.
Role changes away from MEMBER, member removal and ownership transfer invalidate the active identity.
Existing audit records retain operator, namespace/member and before/after grant identity and action reason.

ReviewTask captures:

- auto_publication_grant_id: immutable submission-time grant UUID; absent on all old or excluded tasks.
- submission_may_submit: accepted capability ceiling, not a token or reference requiring live credentials.
- auto_publication_cancelled: irreversible cancellation of this task's automatic eligibility.
- decision_method: nullable until a decision, HUMAN or SYSTEM_AUTOMATIC on decisions.
- decision_scan_task_id: the current completed SAFE scan round used for the automatic decision.
- manual_review_notified: transactional deduplication marker for the existing ReviewSubmittedEvent handoff.

Keep namespace, submittedBy, version and timestamps on the task. System decisions keep reviewedBy NULL,
not the submitter or a fabricated human account. Grant snapshots remain available after revocation.
Never persist plaintext tokens, session secrets or token expiry in the review task.

## Submission and capability ceiling

Transport/authentication accepts submissions through the existing route policy's submit capability (skill:publish).
Keep this mapping centralized in the route policy so #1078 can substitute skill:submit; do not implement its scopes.
Only a legally accepted ordinary submission can capture submission_may_submit=true.
The background path never infers extra capability from worker, retry caller, platform role or refreshed credentials.
Token revocation/expiry and logout affect new requests, not the captured capability of accepted work.

SkillPublishService decides eligibility after resolving and locking the actual Skill and before creating its task.
Require ACTIVE TEAM, current valid MEMBER grant, scanner enabled, requested NAMESPACE_ONLY and either a new Skill
or an already NAMESPACE_ONLY Skill. An existing PRIVATE/PUBLIC Skill targeting NAMESPACE_ONLY is excluded even
if it has never published. Any independent visibility request is excluded. Rerelease must reapply the same checks
and must not gain authority from its membership-bypass flag. GitHub sync and Bundle orchestration never capture grants.
Do not route granted members through forceAutoPublish or SUPER_ADMIN direct publication.
Denied submissions leave database state unchanged; object-storage rollback follows existing compensation rules.

## State transitions and current scan identity

An eligible submission creates a PENDING task and SCANNING version, never PUBLISHED.
A current completed SAFE SecurityAudit may transition SCANNING -> PENDING_REVIEW -> PUBLISHED,
with a retained APPROVED task and SYSTEM_AUTOMATIC decision. Bind the decision to the audit taskId.
SecurityScanService must independently verify taskId/version/scanner, reject processed duplicates,
and prevent stale rounds from changing current version state. ReviewService rechecks the same current SAFE basis.
Absent/disabled/failed/unsafe/timed-out/unknown/stale scans cannot authorize automatic publication.
Existing safety and human-review handling remains authoritative for non-automatic work.

Before automatic approval recheck ACTIVE TEAM, active account, current MEMBER, original submit capability,
matching active grant identity, uncancelled eligibility, ordinary SKILL_VERSION origin, pending task/version,
NAMESPACE_ONLY target and unchanged visibility, active/nonhidden Skill and existing governance constraints.
Do not revive withdrawn, rejected, superseded, yanked, archived or frozen work.

## Concurrency strategy

Use database row locks, not an in-memory lock or a boolean precheck.
Namespace is the serialization boundary shared by grants/revocations and automatic decisions.
Within lifecycle operations use namespace -> Skill -> SkillVersion -> review task order, refreshing managed
entities after waiting for locks. Existing membership mutations lock target user before namespace;
automatic approval must not acquire a user lock after namespace, to avoid reversing that order.
Capture immutable IDs with scalar/preflight reads, then reload state under the relevant lock.
Review optimistic version checks remain the final conditional decision guard.
Apply the same Skill/task serialization to manual review, withdrawal, superseding upload and scan completion.
Do not update an audit/version before acquiring locks that approval needs in the same transaction.
A revocation committed before automatic approval wins; a publication committed first is not rolled back.
Duplicate or late callbacks produce no second publication, latest-pointer movement or publication event.
PostgreSQL tests must prove lock waiting sees committed state rather than stale persistence-context state.

## Revocation and normal-review handoff

Clear the grant and irreversibly cancel pending tasks tied to that member/grant in the same transaction.
Do not change SCANNING or bypass scanning to make a task visible.
Emit the existing ReviewSubmittedEvent once per fallback task and mark manual_review_notified atomically.
Ordinary (never-eligible) submissions keep emitting ReviewSubmittedEvent at submission time; the marker is only
meaningful for tasks that captured a grant. No new channel or unconditional email is introduced;
existing notification preferences/platform configuration govern downstream delivery.
If eligibility is lost through account/namespace/governance changes, the approval-time recheck performs the
same irreversible handoff. A failure or non-SAFE completed scan also hands the task to existing normal processing.
Tasks without a submission-time grant remain manual even when the member is granted later.
A regrant cannot match old snapshots and must never clear cancellation.
Published versions remain published after revocation/removal.

## API and UI

Replace TrustedPublishPolicyController and both old routes with a thin session-only member-management endpoint:
PUT /api/web/namespaces/{slug}/members/{memberId}/team-auto-publication, body {enabled: boolean}.
Return the existing localized ApiResponse<MemberResponse> shape; expose teamAutoPublicationGranted in member listings.
Declare the route explicitly in RouteSecurityPolicyRegistry authorization and session-only catalogs;
all token scopes, including manage/token-manage, must fail before mutation.
Keep actual actor extraction in the controller and policy/audit in the domain service via the application service.

Use namespace-members.tsx, existing components and TanStack Query mutations/invalidation.
Show role, grant status and grant/revoke actions to authorized managers, and explain coverage of browser uploads
and every submit-capable token in this namespace. No separate page or token-management control.
Expose decisionMethod and scan/grant basis in review list/detail/history and outbound review/publication events.
Render localized system/human labels without representing NULL reviewedBy as a human submitter.
All existing locale resources receive keys; regenerate API types from the actual running backend.

## Verification and delivery

Phase commits: design; data/grants; submissions; approvals; web/API; bilingual docs; final gates.
Run focused tests first, then backend reactor and web test/typecheck/lint/build, OpenAPI drift and strict OpenSpec validation.
Use real PostgreSQL/Flyway integration for schema/defaults and concurrency, not H2 or SQL text as a substitute.
Start an isolated backend with empty PostgreSQL/Redis bound to loopback and exercise grants, token denials,
submission/scan outcomes, revocation/regrant and callback duplication through HTTP.
Label local stub results controlled scan callback. A real scanner run, if runnable, is separate evidence.
Sanitize credentials in the journey and remove owned containers/volumes afterward.
#1078-specific read+submit without publish-private combinations remain explicitly pending its separate implementation.
