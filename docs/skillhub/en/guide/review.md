# Review Skills and Suites

This page covers pending-content inspection, decision submission and result verification for authorized reviewers. Authors can use the responsibility table to track submissions. A decision applies to a specific request and version; it does not replace Agent execution testing, compliance certification or global promotion approval.

## Prerequisites and Responsibilities

| Operation | Identity and conditions | Entry |
| --- | --- | --- |
| Review within a team namespace | Namespace Owner or Admin | Review tab in namespace details |
| Review across namespaces | Platform Skill administrator (`SKILL_ADMIN`) or super administrator (`SUPER_ADMIN`) | `/dashboard/reviews` |
| Track an own submission | Submitter | My Review Progress; read access does not grant approval |
| Global promotion and revocation | Submitter and authorized platform reviewer | `/dashboard/promotions`, separate from ordinary version reviews |

Owners and Admins of a team namespace can review their own submissions; super administrators can self-review. Other own submissions cannot be self-reviewed solely through the platform Skill administrator role. Only a super administrator can review their own global promotion request. Ordinary review decisions also require an `ACTIVE` namespace. Source: [review permission policy](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewPermissionChecker.java).

Ordinary public (`PUBLIC`) or namespace-member-visible (`NAMESPACE_ONLY`) uploads enter scanning and review. Private (`PRIVATE`) uploads are retained first and still require publication confirmation or a separate review submission. A previously published version may remain available while an update is pending.

## Inspect Content and Submit a Decision

![Review flow: identify the request, inspect evidence, submit a decision and re-read the outcome](/diagrams/review-decision-flow-en.svg)

Figure: A decision requires a pending request and current permission. Reading the old version does not verify the current submission.

1. Open a pending request and verify its submitter, subject, target version, visibility and namespace. GitHub-synchronized items also provide a content revision, source repository and fixed commit. The technical version used for installation appears in technical information.
2. Inspect the submitted documentation and file tree in Skill Content. Documentation is fetched when the tab is opened; individual file content is fetched on selection. GitHub updates initially open Changes; initial imports and ordinary Skills open Skill Content. An empty preview caused by an object-storage read failure is not evidence of acceptable content.
3. Check license evidence, the scanning report and findings in Source & Security. Distinguish execution errors, risk findings and unconfigured analyzers. Ordinary version approval is unavailable while scanning is running. Unrecognized licenses or missing evidence require inspection of the specific status and supporting material; they do not confirm permission to use the content.
4. For avatar or visibility changes, verify the candidate and requested target. For a Skill Suite, inspect the pending snapshot, Entry Skill and fixed member versions. Approval is unavailable before the snapshot loads. Member approval does not approve the Suite. A Suite definition and a Suite Bundle import operation are separate objects; see [Skill Suites](./skill-suites.html).
5. Read previous decisions in Review History, then approve or reject after evidence checks. A rejection must identify the file, problem and correction conditions; approval comments are optional. Switching tabs preserves comments; switching requests clears comments and unconfirmed dialogs. This version has no conditional-approval state.

| Inspection result | Decision basis | Next action |
| --- | --- | --- |
| Current content, target and evidence agree | The current request has been inspected and no unresolved issue remains | Approve, then re-read the task and target object |
| Files, evidence or declarations contain a specific issue | Record a traceable problem and correction conditions | Reject; the author revises the submission |
| Scan, snapshot or files are unavailable | Evidence required for a decision is missing | Diagnose or wait; do not treat missing results as acceptable evidence |

Online preview limits are listed together below. Exceeding a limit does not mean the file was deleted; obtain it through an authorized download before inspection.

| Item | Fixed limit | Exceeding the limit |
| --- | --- | --- |
| Line-by-line comparison | File exceeds 64KiB or 1000 lines | Line comparison is skipped |
| Current content loaded online | File exceeds 256KiB | Content is not loaded online |
| Baseline download of a removed old file | File exceeds 1MiB | The old file cannot be downloaded from the review page |

These are frontend/API implementation limits, not review-page settings. Sources: [review detail page](https://github.com/iflytek/skillhub/blob/main/web/src/pages/dashboard/review-detail.tsx) and [review API](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/ReviewController.java). `GET /api/v1/reviews/{id}/skill-detail?includeDocumentation=false` reads metadata and the documentation path without fetching object-storage content; the default is `true`.

### Inspect Compliance Declarations

Versions containing `x-astron-compliance` display a compliance snapshot and added, removed or modified declarations. A changed `complianceSnapshot.digest` indicates a change in normalized declarations. Match declarations to actual capability, control items, evidence paths and external material rather than standard names alone. Inaccessible evidence, mismatched content or removals affecting downstream references require a documented issue and revision. The registry stores structured declarations and version snapshots; it does not certify the author's actual compliance. See [compliance metadata](./skill-publish.html).

## State Object Reference

A task can be `PENDING` while its version is still `SCANNING`. Task and version states must not be substituted for each other. Read the following four objects separately.

### Review Task States

| State | Meaning | Responsible party and next action |
| --- | --- | --- |
| `PENDING` | Awaiting a decision | Authorized reviewer reads the current request before acting |
| `APPROVED` | Approved | Submitter verifies the version or metadata effect |
| `REJECTED` | Rejected with comments retained | Submitter revises and resubmits |
| `SUPERSEDED` | Replaced by a later request | Submitter and reviewer read the current request |

The [task enum](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewTaskStatus.java) has no `WITHDRAWN`. Withdrawing an ordinary Skill version removes the pending task and changes its version state. It does not remove a previously published version or revoke an approved global promotion; see the [withdrawal service](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewService.java).

### Skill Version States

| State | Meaning | Responsible party and next action |
| --- | --- | --- |
| `DRAFT` | Version draft, not an authoring API draft object | Skill owner completes preparation |
| `SCANNING` | Scan is running | Scanning service completes before continuation |
| `SCAN_FAILED` | Scan execution failed | Operator and owner diagnose before retrying |
| `UPLOADED` | Uploaded but not published | Owner confirms publication or submits for review |
| `PENDING_REVIEW` | Awaiting a human decision | Authorized reviewer inspects this version |
| `PUBLISHED` | Published | Users access under visibility rules; execution needs separate verification |
| `REJECTED` | Not approved | Owner revises and resubmits |
| `YANKED` | Withdrawn from distribution | Users select an installable alternative |

Source: [version enum](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillVersionStatus.java).

### Suite Bundle Import Operation States

| State | Meaning | Responsible party and next action |
| --- | --- | --- |
| `RUNNING` | Executing | Importer tracks the original operation without duplicate confirmation |
| `WAITING_FOR_MEMBERS` | Waiting for members | Import service and member reviewers handle members |
| `BLOCKED_RETRYABLE` | Retryable block | Importer diagnoses and retries the original operation |
| `REPREVIEW_REQUIRED` | Previous preview cannot continue | Importer previews again and checks differences |
| `SUITE_DRAFT_CREATED` | Suite draft created, not approved | Suite owner continues publication |
| `CANCELLED` | Cancelled; not a full rollback | Importer checks completed member actions |

Source: [import states](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/suite/bundle/SkillSuiteBundleOperationStatus.java). Recovery instructions are in [Suite Bundle Import](./suite-bundle.html).

### Avatar Review Results

Avatars reuse Skill review requests, but their state dimensions remain separate. A machine block is not a human rejection.

| Field | Values and meanings | Responsible party and next action |
| --- | --- | --- |
| Candidate `status` | `PENDING` pending, `ACTIVE` in use, `REJECTED` rejected, `SUPERSEDED` replaced, `REMOVED` removed | Owner and reviewer compare the candidate with the active avatar; the response field is `candidateStatus` |
| `machineStatus` | `SKIPPED` skipped, `PENDING` pending, `PROCESSING` running, `PASSED` allowed, `BLOCKED` blocked, `ERROR` execution error | Machine service processes it; deployment operator diagnoses `ERROR` |
| `reviewStatus` | `NOT_REQUIRED` no human review, `PENDING` pending, `APPROVED` approved, `REJECTED` rejected, `APPEAL_PENDING` appeal pending | Authorized reviewer and owner act on current comments |

Source: [avatar candidate model](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillAvatarCandidate.java). Configuration and retry rules are in [Skill Avatars](./skill-avatars.html).

## Completion Criteria and Failure Handling

After ordinary version approval, the task is `APPROVED` and the target version is `PUBLISHED`; verify access with the intended identity. Metadata requests require checking the actual metadata effect, not only the version number. A rejected ordinary version is `REJECTED` with correction comments; the author revises and resubmits from the current-version entry. Approval does not prove Agent execution success or eliminate security risk.

| Symptom | Likely cause | Diagnosis | Resolution |
| --- | --- | --- | --- |
| Request is listed but cannot be approved | Self-review, namespace state or identity condition is not met | Compare submitter, roles and namespace state | Assign an eligible reviewer; do not alter requests to bypass restrictions |
| Approval button is disabled | Scan/avatar is pending or Suite snapshot failed to load | Read scan, candidate and snapshot errors | Complete dependencies or retry reads; do not treat absent results as acceptable evidence |
| File or documentation is empty | Object read failed or preview limit was exceeded | Check file size and preview error; download with permission | Obtain readable material before deciding; do not approve an empty preview |
| Decision reports a state conflict | Task was already processed or superseded | Refresh history and compare task ID and current state | Follow the new state; do not repeatedly approve or reject |

## Global Revocation and Governance Boundaries

If deletion of a promoted Skill is blocked, request revocation from Promote to Global on its source Skill. A platform Skill administrator or super administrator handles it under Revocation Pending Review at `/dashboard/promotions`; own submissions follow the self-review restrictions above. After the revocation record is `APPROVED`, verify that the active relation is removed before deleting the source Skill with the required permission. Hiding, archiving or withdrawing ordinary review does not substitute for revocation.

When no revocation request is pending, an authorized platform operator can revoke directly from details with a retained record. An existing pending request must be handled in the queue. Approved revocation deletes the entire global Skill, its versions, reviews and statistics, while retaining the source Skill. Client files cannot be retrieved, and the old address can be reused. If deletion remains blocked after approval, check for another promotion and record object IDs, request ID, deployed version and failed response. Do not modify database relations directly. Source: [revocation service](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/PromotionRevocationService.java).

Reports are handled at `/dashboard/reports` under existing governance permissions and the specific report evidence. Investigating a report does not automatically approve or reject a version. Withdrawal applies only to a pending review; historical comments and current request mutability must be evaluated separately.

## Further Reading

- [Publish and Update Skills](./skill-publish.html): Preparation, submission and version management.
- [Security Scanning](./scanner.html): Interpret reports and diagnose execution failures.
- [Manage Namespaces](./namespace.html): Membership roles and namespace states.
