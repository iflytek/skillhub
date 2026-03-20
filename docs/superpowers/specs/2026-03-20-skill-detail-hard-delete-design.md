# Skill Detail Hard Delete Design

## Context

The project already has a hard-delete API for whole skills at `DELETE /api/v1/skills/{namespace}/{slug}`. That API is token-oriented and restricted to `SUPER_ADMIN` callers with the `skill:delete` scope. The product now needs a skill-detail-page deletion flow that lets skill owners delete their own skills from the UI while keeping the existing token API restricted to super administrators.

This deletion must remain a physical delete of the whole skill and all versions. The UI must require multiple confirmations, and the backend must be resilient if object storage cleanup fails after the database delete commits.

## Goals

- Add a skill-detail-page delete flow under lifecycle management.
- Allow deletion from the UI for:
  - the skill owner
  - `SUPER_ADMIN`
- Keep the existing token hard-delete API restricted to `SUPER_ADMIN`.
- Require two-stage user confirmation in the UI.
- Continue performing whole-skill physical deletion.
- Make storage cleanup failure recoverable through persistent compensation.

## Non-Goals

- No change to the existing token API permission model.
- No soft delete.
- No partial delete of a single version.
- No change to the user-confirmed idempotent semantics for missing skills.

## User Experience

### Entry Point

In the skill detail page lifecycle management section, add a destructive action for deleting the skill.

Visibility rules:
- visible to the skill owner
- visible to `SUPER_ADMIN`
- hidden for all other users

### Confirmation Flow

The deletion flow uses two steps:

1. Danger confirmation dialog
- explains that the delete is permanent
- explains that all versions, files, and download bundles will be removed
- explains that the action cannot be undone

2. Input confirmation dialog
- requires the user to type the skill `slug`
- the destructive confirm button remains disabled until the input exactly matches the current skill slug

### Success Behavior

After success:
- show a success toast
- navigate away from the deleted detail page
- prefer returning to the logical source page if available
- otherwise fall back to the current default listing route

### Failure Behavior

If the delete request fails:
- keep the user on the detail page
- show a destructive error toast with the backend message when available
- keep the skill data unchanged on screen until a successful delete happens

## API Design

### Existing API (unchanged)

`DELETE /api/v1/skills/{namespace}/{slug}`
- for API token callers
- still requires `SUPER_ADMIN`
- still requires `skill:delete`

### New Portal API

Add a session-authenticated lifecycle endpoint under the existing skill lifecycle controller family.

Recommended route:
- `DELETE /api/web/skills/{namespace}/{slug}`

Authorization:
- allow if current user is the skill owner
- allow if current user has `SUPER_ADMIN`
- reject all others

Behavior:
- normalize `@namespace` input the same way as current lifecycle endpoints
- if the skill is missing, return a success envelope with `deleted=false`
- if present and authorized, perform whole-skill hard delete and return `deleted=true`

Response shape:
- reuse the existing delete response DTO shape when practical
- include at minimum:
  - `skillId` nullable
  - `namespace`
  - `slug`
  - `deleted`

## Backend Design

### App Layer Split

Keep the current split between controller, app service, and domain delete service.

- controller: request extraction and response envelope only
- app service: authorization-aware orchestration for the portal use case
- domain service: actual delete of database-linked skill resources

### Authorization Model

Portal delete authorization in app service:
- load the skill by namespace and slug
- if missing, return idempotent result
- allow if `principal.userId == skill.ownerId`
- allow if roles contain `SUPER_ADMIN`
- otherwise throw `DomainForbiddenException`

This keeps owner-aware logic out of the token controller and preserves the existing admin-only token endpoint.

### Deletion Scope

The delete still removes:
- the skill row
- all skill versions
- all skill files
- all stored bundles
- all stored file objects
- tags
- reports
- review tasks for the deleted versions
- promotion requests referencing the skill
- stars
- ratings
- version stats
- search index document

### Storage Failure Compensation

Current implementation deletes storage after transaction commit and only logs failures. That is not enough for this feature.

Replace that behavior with persistent compensation:

1. During deletion, collect all storage keys that must be removed.
2. Commit the database transaction first.
3. After commit, try deleting those storage keys.
4. If storage deletion fails, persist a compensation record containing:
- skill identifier context
- storage keys still pending deletion
- retry count
- last error
- timestamps
5. A scheduled cleanup task retries pending compensation records until success.
6. On successful retry, mark the compensation record completed or delete it.

This avoids coupling database commit to S3/network availability while still guaranteeing that failed storage cleanup remains actionable and retryable.

## Data Model

Add a persistent compensation table for hard-delete storage cleanup.

Suggested fields:
- `id`
- `skill_id` nullable
- `namespace_slug`
- `skill_slug`
- `storage_keys_json`
- `status` (`PENDING`, `FAILED`, `COMPLETED`)
- `attempt_count`
- `last_error`
- `created_at`
- `updated_at`
- `last_attempt_at`

Keep the model narrow and purpose-built for storage cleanup retries.

## Frontend Design

### Skill Detail Page

Update the lifecycle section in `skill-detail.tsx`:
- add delete action only when caller is owner or super admin
- keep archive/unarchive behavior unchanged
- visually separate delete from archive/unarchive because it is destructive and permanent

### Dialog State

Add two pieces of dialog state:
- first confirmation open/closed
- second input-confirmation open/closed plus typed slug value

The second dialog opens only after the first is confirmed.

### Mutation Handling

Add a dedicated delete mutation hook using the new portal endpoint.

On success:
- invalidate affected skill queries
- toast success
- navigate away from the deleted page

On failure:
- toast error
- keep dialogs closed after failure only if that matches the current destructive-action convention; otherwise preserve the second dialog for retry

## Testing Strategy

### Backend Tests

Add tests for:
- portal delete allows owner deleting own skill
- portal delete allows `SUPER_ADMIN`
- portal delete rejects non-owner non-admin
- portal delete remains idempotent for missing skill
- token delete path remains `SUPER_ADMIN`-only
- hard delete still removes all linked data
- storage cleanup failure creates compensation record
- compensation retry task succeeds and clears pending work

### Frontend Tests

Add tests for:
- delete button visibility for owner / super admin / unauthorized user
- first confirmation dialog opens from lifecycle section
- second confirmation requires exact slug input
- mutation is not called before exact slug input
- success toast and navigation happen after successful delete
- failure toast appears when delete fails

## Risks and Mitigations

### Risk: widening delete permissions accidentally affects token callers
Mitigation:
- keep existing token controller and route rules unchanged
- add owner-aware behavior only on the new portal delete path

### Risk: deleted page navigation becomes confusing
Mitigation:
- reuse existing return-source helpers where possible
- define one clear fallback route

### Risk: storage cleanup failure leaves orphaned files
Mitigation:
- persist compensation tasks and retry until cleanup succeeds

## Files Expected To Change

Backend:
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillLifecycleController.java` or a dedicated portal delete controller
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SkillDeleteAppService.java`
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillHardDeleteService.java`
- new compensation entity/repository/service/task files
- migration file for compensation table
- route and auth tests if needed

Frontend:
- `web/src/pages/skill-detail.tsx`
- `web/src/shared/hooks/use-skill-queries.ts`
- locale files and new tests as needed

## Recommendation

Implement this as a new portal-only delete path while preserving the existing token path. Reuse the current hard-delete core, but upgrade storage cleanup from best-effort logging to persistent compensation and retry. This keeps the security model narrow, meets the UI requirement cleanly, and closes the current operational gap around storage failures.
