# Review & Governance

## Overview

SkillHub provides a complete review workflow to ensure that skill packages published to the registry comply with team standards.

The review mechanism operates on two levels:
- **Namespace Review**: Team administrators review skill packages within their namespace
- **Platform Review**: Platform administrators review skill packages promoted to the global scope

![Concept Diagram](/diagrams/review-concept.png)

**Review Process**:

1. A developer publishes a skill package -> it enters the "Pending Review" state
2. An administrator receives a notification -> reviews the skill package details
3. The administrator makes a decision -> approve or reject
4. Upon approval -> the skill package is officially published
5. Upon rejection -> the developer receives feedback and can revise and resubmit

**Review Statuses**:

| Status | Description |
|------|------|
| **PENDING** | Awaiting review |
| **APPROVED** | Approved |
| **REJECTED** | Rejected |
| **WITHDRAWN** | Withdrawn |

**Governance Features**:

- **Review Workflow**: Per-item review of versions, profile changes, and Suites
- **Reporting System**: Users can report inappropriate skill packages
- **Promotion Management**: Promote namespace skill packages to the global scope
- **Audit Logs**: All governance actions are recorded

## Identify what is being reviewed

Review-task status and skill-version status are different: a task can be PENDING while the uploaded version is still SCANNING. Wait for scanning and inspect findings before deciding. Approval of a skill version does not approve a Suite that references it. Suites have their own review, while Bundle import first completes member publishing and then creates a Suite draft. See [Skill Suites](/en/guide/skill-suites) and [Bundle import](/en/guide/suite-bundle).

## Use Cases

**Case 1: Namespace Administrator Review**

A team administrator reviews skill packages submitted by team members to ensure compliance with team standards.

![Screenshot](/screenshots/review-list.png)

**Case 2: Platform Administrator Reviews Promotions**

A platform administrator reviews skill packages being promoted from a namespace to the global scope.

**Case 3: Report Handling**

A user reports an inappropriate skill package, and an administrator investigates and takes action.


## Step-by-Step Guide

**Submitting for Review**:

1. Ordinary PUBLIC / NAMESPACE_ONLY uploads create review tasks; PRIVATE uploads require confirmation or a separate review submission
2. The developer can check the review status under "My Submissions"
3. Wait for an administrator to review

**Reviewing Skill Packages**:

1. Navigate to `/dashboard/reviews`
2. View the pending review list
3. Click on a skill package to view details:
   - Review metadata (name, description, version)
   - Browse the file list
   - View file contents online
   - Download the full package for local testing
   - Review the compliance snapshot and the diff from the previous published version

![Flow Diagram](/diagrams/review-flow.png)

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

**Withdrawing a Review**:

If a developer discovers an issue, they can withdraw the submission before it is approved:

1. Navigate to "My Submissions"
2. Find the skill package pending review
3. Click "Withdraw"
4. Confirm the withdrawal

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
```bash
POST /api/v1/reviews
Content-Type: application/json

{
  "skillVersionId": 123
}
```

**Approve a Review**:
```bash
POST /api/v1/reviews/{id}/approve
Content-Type: application/json

{
  "comment": "Looks good! Approved."
}
```

**Reject a Review**:
```bash
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
```bash
GET /api/v1/reviews/pending?namespaceId=123&page=0&size=20
```

**List My Submissions**:
```bash
GET /api/v1/reviews/my-submissions?page=0&size=20
```

**Get Review Details**:
```bash
GET /api/v1/reviews/{id}
```

**Get Skill Package Details Under Review**:
```bash
GET /api/v1/reviews/{id}/skill-detail
```

**Download Review Package**:
```bash
GET /api/v1/reviews/{id}/download
```

**Withdraw a Review**:
```bash
POST /api/v1/reviews/{id}/withdraw
```

**Report a Skill Package**:
```bash
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
