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

- **Review Workflow**: Multi-level review, batch review
- **Reporting System**: Users can report inappropriate skill packages
- **Promotion Management**: Promote namespace skill packages to the global scope
- **Audit Logs**: All governance actions are recorded

## Use Cases

**Case 1: Namespace Administrator Review**

A team administrator reviews skill packages submitted by team members to ensure compliance with team standards.

![Screenshot](/screenshots/review-list.png)

**Case 2: Platform Administrator Reviews Promotions**

A platform administrator reviews skill packages being promoted from a namespace to the global scope.

**Case 3: Report Handling**

A user reports an inappropriate skill package, and an administrator investigates and takes action.

**Case 4: Batch Review**

An administrator batch-approves multiple skill packages that meet the standards.

## Step-by-Step Guide

**Submitting for Review**:

1. When a skill package is published, the system automatically creates a review task
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

![Flow Diagram](/diagrams/review-flow.png)

4. Make a decision:
   - **Approve**: The skill package is officially published and the developer is notified
   - **Reject**: Provide a rejection reason; the developer can revise and resubmit

5. Add review comments (optional)

**Withdrawing a Review**:

If a developer discovers an issue, they can withdraw the submission before it is approved:

1. Navigate to "My Submissions"
2. Find the skill package pending review
3. Click "Withdraw"
4. Confirm the withdrawal

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
  "skillVersionId": "version-123"
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
| id | string | Review task ID (path parameter) |
| comment | string | Review comment (optional, max 1000 characters) |

**List Pending Reviews**:
```bash
GET /api/v1/reviews/pending?namespaceId=ns-123&page=0&size=20
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

> **Review Permissions**: Only namespace Admins and Owners can review skill packages within their namespace. Platform administrators can review all skill packages.

- **Review Turnaround**: It is recommended to complete reviews within 24 hours to avoid blocking developers
- **Review Records**: All review actions are recorded in the audit log
- **Batch Review**: Administrators can batch-approve multiple skill packages
- **Review Comments**: When rejecting, it is recommended to provide detailed improvement suggestions
- **Withdrawal Restrictions**: Only skill packages in the pending review state can be withdrawn
