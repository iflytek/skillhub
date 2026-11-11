# Team-only Automatic Publication

Team-only automatic publication is a permission granted to an individual ordinary member of a `TEAM` namespace. After it is granted, that member's eligible team-only submissions in the namespace are approved and published by the system once the **current security scan round is explicitly safe**. It is not a new role, a namespace switch or a token scope. A review task is still created and retained; the system records the approval decision.

## Scope

| Case | Applies |
| --- | --- |
| A new Skill first published directly as namespace-only (`NAMESPACE_ONLY`) | Yes |
| A new version of a Skill that is already namespace-only, with the same target visibility | Yes |
| A request to change a private Skill to namespace-only (independent visibility request) | No; normal review |
| A new version uploaded with a target visibility different from the current one (for example an existing private Skill uploaded as namespace-only) | No |
| Public publishing, global promotion or any visibility expansion | No |
| Rerelease paths that reuse publishing logic | Same rules as above; not applicable when the target visibility changes |
| GitHub synchronization, Skill Suite and Bundle import orchestration | No; existing rules apply |
| `GLOBAL` or personal namespaces | No |

The permission covers the member's browser submissions in this namespace and every API token the member holds with submit capability. Browser and token submissions follow the same policy. A token still needs submit capability (currently `skill:publish`); read-only tokens cannot upload. This feature does not mean that any token preset never needs human review.

## Grant and Revoke

1. Sign in to the web console as an Owner or Admin of the namespace.
2. Open Member Management. In a `TEAM` namespace the member table shows a Team-only automatic publication column: ordinary members show Granted or Not granted; Owners and Admins show Not applicable.
3. Select Grant or Revoke on the member row, read the scope explanation in the confirmation dialog, and confirm.

Only browser sessions can perform this operation. The endpoint is `PUT /api/web/namespaces/{slug}/members/{userId}/team-auto-publication` (the same path exists under `/api/v1`) with body `{"granted": true}` or `{"granted": false}`, a session cookie and the CSRF header. Every API token is rejected, including tokens with `token:manage`, `skill:delete` or other scopes. Ordinary members cannot grant themselves.

Boundaries:

- Not granted by default and available only to the `MEMBER` role. A grant does not change the role and gives no review, deletion, namespace management, private publication confirmation or cross-namespace permission.
- Member management is read-only in frozen or archived namespaces.
- Each transition from not granted to granted creates a new grant identity; granting again while already granted keeps the identity.
- Grants, revocations and revocations caused by role changes, member removal, ownership transfer or account merge are written to the audit log with the actual operator and the before/after grant identity.

## What Happens After Submission

1. When the member submits an eligible version, the review task records the grant identity active at that time. A grant affects only later submissions; tasks already pending do not gain eligibility.
2. The version enters scanning (`SCANNING`) as usual and is never published before the scan finishes.
3. When the current scan round finishes, the system re-checks within one transaction: the namespace is still an `ACTIVE` `TEAM`; the submitter is still a valid `MEMBER`; the grant identity on the task equals the member's current active grant; the version is still pending and has not been superseded by a later upload; and the verdict belongs to the version's **current** scan round and is `SAFE`.
4. If all checks pass, the task becomes `APPROVED`, the version becomes `PUBLISHED`, the decision method is recorded as System automatic approval (`SYSTEM_AUTOMATIC`) together with the scan task it relied on. The reviewer field stays empty; the submitter is never recorded as reviewer.
5. If any check fails, the task falls back to normal review. It stays in the pending queue for namespace reviewers, and reviewers are notified once through the existing pending-review notification mechanism, respecting their notification preferences.

Disabled scanning, execution failure, unsafe or suspicious verdicts, timeouts, unknown verdicts and stale results never lead to automatic approval. A temporarily unavailable scanner follows the existing deferred retry; a final failure moves the task to normal review.

## Revocation, Member Removal and Re-granting

- Revoking the grant, removing the member, changing the role or otherwise losing eligibility immediately moves all of the member's pending tasks still awaiting automatic approval to normal review. A task that is still scanning keeps scanning and then follows normal review.
- Granting again creates a new grant identity and **does not revive** old tasks; they still need human review.
- Published versions are not withdrawn on revocation.
- Revoking a token, token expiry or logout does not cancel submissions that were already accepted. The system never retries as a worker or higher-privileged identity to add capability the original request lacked. Plaintext tokens are never stored.

## Recognize System Approval in Review Pages

The review list, the Review History of review details and My Review Progress show the decision method:

- System automatic approval: shows the scan task it relied on and no reviewer.
- Human review: shows the actual reviewer.

Tasks that fall back to normal review appear in the review center like any other pending task; reviewers handle them as described in [Review Skills and Suites](./review.html).

## Completion Criteria and Troubleshooting

| Symptom | Likely cause | Resolution |
| --- | --- | --- |
| No Grant button on a member row | Current account is not Owner or Admin, namespace is frozen or archived, or the target is not a `MEMBER` | An authorized operator acts; do not modify requests to bypass permission |
| The grant endpoint returns 403 | An API token was used, or the browser session or CSRF header is missing | Use a browser session |
| Granted, but the version still waits for human review | Submitted before the grant, target visibility out of scope, scan not `SAFE`, or the grant was revoked | Read the decision method and scan result in review details; reviewers handle it normally |
| Old task still pending after re-granting | By design: the old task is bound to the old grant identity | Review it manually, or withdraw and resubmit |

## Further Reading

- [Manage Namespaces](./namespace.html)
- [Review Skills and Suites](./review.html)
- [Accounts and Access Tokens](./account-access.html)
- [Security Scanning and Reports](./scanner.html)
