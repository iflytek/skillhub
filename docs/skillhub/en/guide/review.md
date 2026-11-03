# Review Skills and Suites

## Reviewer Checklist

1. Verify the subject, submitter and target version. A skill version, profile/avatar change and Suite version require different checks.
2. Read the submitted files and changes, not only the current public version. The previous published version may remain downloadable during review.
3. Inspect scan status and findings. Execution failure, detected risk and an unconfigured analyzer are different; an empty report is not a security conclusion.
4. Check visibility and intended audience, including every Suite member and its entry role. Member approval does not approve the Suite.
5. Before approval, ensure current data is still valid. For rejection, identify files, problems and required changes. After a failed decision request, reload status before trying again.
6. Verify the decision history and resulting published version. Review success does not test skill execution in a local Agent.

### Tracking and Revising as an Author

Ordinary `PUBLIC / NAMESPACE_ONLY` uploads create review tasks; a `PRIVATE` upload is retained for explicit publishing confirmation or separate review submission. Use My Submissions to inspect tasks and feedback; an authorized submitter selects Withdraw on the pending task and confirms. Withdrawal stops that review; it does not delete the prior published version or undo an approved global promotion. After rejection, revise according to the comments and use the supported resubmission action. A successful upload does not by itself reopen review.


## Identify the State Object

Identify the object first. A task can be `PENDING` while its version is `SCANNING`; the task state does not replace the version's `PENDING_REVIEW`.

### Review Task States

| State | Meaning | Responsible party | Next action |
| --- | --- | --- | --- |
| `PENDING` | Awaiting a decision | Authorized namespace reviewer or platform Skill Admin | Inspect the submitted content and scan result before deciding |
| `APPROVED` | The task was approved | Submitter | Verify the resulting version or profile change |
| `REJECTED` | Rejected with feedback | Submitter | Revise and submit again |
| `SUPERSEDED` | Replaced by a later request | Submitter and reviewer | Find the current request; do not decide the old task |

[ReviewTaskStatus](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewTaskStatus.java) has no `WITHDRAWN` value. Withdrawal is an operation, not evidence of a similarly named enum: the skill-version withdrawal path deletes the pending task and updates the version. See [ReviewService](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewService.java).

### Skill Version States

These values belong to a version; `DRAFT` is not the authoring API draft object.

| State | Meaning | Responsible party | Next action |
| --- | --- | --- | --- |
| `DRAFT` | Draft version | Skill owner | Finish preparation; do not treat it as published |
| `SCANNING` | Scan in progress | Scanner | Wait for the report before approving ordinary review submissions |
| `SCAN_FAILED` | Scan execution failed | Operator and skill owner | Diagnose and retry; this is not a risk verdict |
| `UPLOADED` | Uploaded but not published | Skill owner | Use the available confirmation or submission action |
| `PENDING_REVIEW` | Awaiting a decision | Authorized reviewer | Inspect this version and decide |
| `PUBLISHED` | Published version | User and skill owner | Apply normal visibility rules; Agent execution is not thereby verified |
| `REJECTED` | Version not approved | Skill owner | Revise and resubmit |
| `YANKED` | Version withdrawn from distribution | Skill owner and user | Check an alternative version rather than assuming installability |

See [SkillVersionStatus](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillVersionStatus.java) and [publishing and updating skills](./skill-publish.html).

### Suite Bundle Operation States

| State | Meaning | Responsible party | Next action |
| --- | --- | --- | --- |
| `RUNNING` | Import is running | Import service | Track the original operation; do not confirm it twice |
| `WAITING_FOR_MEMBERS` | Waiting for member versions | Import service and member reviewers | Inspect member publishing or review progress |
| `BLOCKED_RETRYABLE` | Retryable blocker | Import author | Resolve the blocker, then retry the original operation |
| `REPREVIEW_REQUIRED` | Previous preview cannot be confirmed | Import author | Preview again and inspect differences |
| `SUITE_DRAFT_CREATED` | Suite draft created | Suite owner | Continue publishing in Suite management; this is not approval |
| `CANCELLED` | Operation cancelled | Import author | Inspect completed member actions; do not assume a full rollback |

See [SkillSuiteBundleOperationStatus](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/suite/bundle/SkillSuiteBundleOperationStatus.java) and [Bundle recovery](./suite-bundle.html).

### Avatar Review Results

An avatar candidate has three state dimensions and reuses the skill review request; it is not another single review-task enum.

| Field and values | Meaning | Responsible party | Next action |
| --- | --- | --- | --- |
| Candidate `status`: `PENDING`, `ACTIVE`, `REJECTED`, `SUPERSEDED`, `REMOVED` | Waiting, in use, rejected, replaced, removed | Skill owner and authorized reviewer | Inspect the current candidate; an old candidate is not the active avatar |
| `machineStatus`: `SKIPPED`, `PENDING`, `PROCESSING`, `PASSED`, `BLOCKED`, `ERROR` | Skipped, waiting, running, allowed, blocked, execution error | Machine service and operator | Diagnose `ERROR`; `BLOCKED` is not a human rejection |
| `reviewStatus`: `NOT_REQUIRED`, `PENDING`, `APPROVED`, `REJECTED`, `APPEAL_PENDING` | No manual review, waiting, approved, rejected, appeal pending | Authorized reviewer and skill owner | Read the decision and use the current candidate's allowed action |

See [SkillAvatarCandidate](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillAvatarCandidate.java); the response exposes candidate `status` as `candidateStatus`. Configuration, result locations and retry behavior are described in [skill avatar review](./skill-avatars.html).

## Review Entry Points

Role and self-review limits appear at the end of this page. Reports and promotion requests are handled separately.

![Review subjects and responsibilities](/diagrams/review-concept.png)

![Review task list](/screenshots/review-list.png)

## Step-by-Step Guide

**Reviewing Skill Packages**:

1. Navigate to `/dashboard/reviews`
2. View the pending review list
3. Click on a skill package to view details:
   - Review metadata (name, description, version)
   - Browse the file list
   - View file contents online
   - Download the full package for local testing
   - Review the compliance snapshot and the diff from the previous published version

![Scan and review in the ordinary publication path](/diagrams/skill-publish-lifecycle-sketch.png)

The review section follows the PUBLIC / NAMESPACE_ONLY branch: scanning completes before the version waits for a review decision.

4. Make a decision:
   - **Approve**: The skill package is officially published and the developer is notified
   - **Reject**: Provide a rejection reason; the developer can revise and resubmit

5. Add review comments (optional)

**Reviewing Compliance Declarations**:

If the pending version contains `x-astron-compliance`, the review detail page shows the version-level compliance snapshot and a diff summary:

- Added declarations: standards, controls, or evidence newly added by the pending version.
- Removed declarations: declarations that existed in the previous published version and are no longer present.
- Modified declarations: standard metadata, control title, or evidence changed.
- Digest changes: `complianceSnapshot.digest` changed, which means the normalized declaration content changed.

Review guidance:

1. Check whether each declaration matches the actual skill behavior. For example, a security response skill that declares a MITRE ATT&CK technique should provide supporting documentation or packaged evidence.
2. Expand diff items to inspect evidence paths, external links, and digests instead of relying only on the declaration title.
3. Treat removals and broad rewrites as higher-priority review items because downstream audit systems may reference those snapshots.
4. Reject the submission if evidence is missing, paths are inaccessible, or declarations clearly do not match the skill capability.

SkillHub guarantees structural validation, traceable evidence references, and immutable version snapshots. It does not certify that the author's declaration is objectively compliant.

### Deleting a Promoted Skill

If permanent deletion reports "Use the reviewed revocation workflow to remove a promoted skill", manage the global publication from the source team skill's detail page. Hiding, archiving, or withdrawing a pending submission does not remove an approved global skill. Submitting a revocation request does not complete revocation either.

1. Open the source skill, such as `@it/translate-document`. In "Promote to Global", choose "Request global revocation", enter a reason, and confirm.
2. Ask a platform `SKILL_ADMIN` or `SUPER_ADMIN` to open "Revocations to review" at `/dashboard/promotions`, verify the source and global skill, and choose "Approve revocation". A `SKILL_ADMIN` cannot review their own request; a `SUPER_ADMIN` can.
3. Refresh the source skill and check that the current request shows "Revoked" (`APPROVED`) in "Revocation history". `PENDING` or `REJECTED` means that this request has not completed revocation; ordinary deletion remains blocked while an active global link exists. A historical request's status is not a substitute for checking the current promotion relationship.
4. Approval deletes the linked `@global/translate-document` and all its global versions while keeping the source team skill. To remove the source too, use its permanent-delete action afterward with the required deletion permissions.

A platform `SKILL_ADMIN` or `SUPER_ADMIN` can also choose "Revoke directly as admin" on the source detail page when no revocation request is pending. Confirmation completes global revocation immediately and retains the revocation record. When a request is already pending, the detail page only shows its pending status; handle it through "Revocations to review" as described above. Check the confirmation carefully: this removes the entire global skill, including independently uploaded global versions, ratings, and statistics. Installed client files cannot be recalled, and another skill may later use the same address.

If the revocation record is already `APPROVED` but the same error persists after refreshing, record the source/global skill IDs, revocation request ID, deployed version or image tag, and the deletion response and request ID. Also check whether a new global promotion was approved after revocation. If the revocation controls are missing, first verify that the deployment and database migrations match the intended version and ask a maintainer to investigate. Do not edit promotion relationships directly in the database to bypass the workflow.

**Handling Reports**:

1. Navigate to `/dashboard/reports`
2. View the report list
3. Investigate the reported content
4. Take action (archive the skill package, warn the user, etc.)

## API Reference

**Submit for Review**:
```http
POST /api/v1/reviews
Content-Type: application/json

{
  "skillVersionId": 123
}
```

**Approve a Review**:
```http
POST /api/v1/reviews/{id}/approve
Content-Type: application/json

{
  "comment": "Looks good! Approved."
}
```

**Reject a Review**:
```http
POST /api/v1/reviews/{id}/reject
Content-Type: application/json

{
  "comment": "Please fix the documentation and add more examples."
}
```

**Parameter Reference**:
| Parameter | Type | Description |
|------|------|------|
| id | number | Review task ID (path parameter) |
| comment | string | Review comment (optional for approval; provide a clear reason for rejection) |

**List Pending Reviews**:
```http
GET /api/v1/reviews/pending?namespaceId=123&page=0&size=20
```

**List My Submissions**:
```http
GET /api/v1/reviews/my-submissions?page=0&size=20
```

**Get Review Details**:
```http
GET /api/v1/reviews/{id}
```

**Get Skill Package Details Under Review**:
```http
GET /api/v1/reviews/{id}/skill-detail
```

**Download Review Package**:
```http
GET /api/v1/reviews/{id}/download
```

**Withdraw a Review**:
```http
POST /api/v1/reviews/{id}/withdraw
```

**Report a Skill Package**:
```http
POST /api/v1/skills/{namespace}/{slug}/reports
Content-Type: application/json

{
  "reason": "INAPPROPRIATE_CONTENT",
  "details": "This skill contains malicious code"
}
```

## Notes

> **Review Permissions**: Team namespace Admins and Owners review their space; SKILL_ADMIN and SUPER_ADMIN have platform review roles. Personal spaces and self-review have additional rules; a generic administrator label is not sufficient.

- **Review Turnaround**: It is recommended to complete reviews within 24 hours to avoid blocking developers
- **Review Records**: All review actions are recorded in the audit log
- **Compliance Audit**: Compliance declarations are recorded as version snapshots. Approval or rejection should consider the diff summary, but Agent execution traces are not recorded by SkillHub
- **Review Comments**: When rejecting, it is recommended to provide detailed improvement suggestions
- **Withdrawal Restrictions**: Only skill packages in the pending review state can be withdrawn
