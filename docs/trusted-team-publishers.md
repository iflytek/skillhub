# Trusted TEAM publishers (opt-in)

A TEAM namespace may let individual `MEMBER` accounts publish `NAMESPACE_ONLY` skills without a human review decision. The default is **off** for every namespace and member. `PUBLIC` and global promotion still require the normal review process. This does not grant skill-management or review permissions to the member. `PRIVATE` publishing is unchanged.

The owner or an admin uses a browser session (not an API token) to configure the policy:

- `PUT /api/web/namespaces/{slug}/trusted-publish-policy` with `{"enabled":true}` (or `false` to disable); `GET` reads the effective namespace setting.
- `PUT /api/web/namespaces/{slug}/members/{memberId}/review-exemption` with `{"enabled":true}` (or `false` to revoke). The existing members listing includes `reviewExempt`. The grant is available only for `MEMBER` roles in an active TEAM namespace. Removing the member or changing the role removes eligibility.

Both switches must be on when publishing and when a scanner result arrives. The publisher uses the existing `skill:publish` API token; no password or admin role is needed in the publisher runtime. A pending review task is recorded with an auto-approval marker; after the **current** security scan returns `SAFE`, the existing approval transition publishes the version and marks the task `APPROVED` with a system-auto-approval comment. Unsafe, failed, late, or absent scans do not auto-publish. Disabling the policy or revoking the grant before scan completion leaves the pending task for manual review. Existing pending tasks are never retroactively marked for auto-approval.

Rollout: migrate the three default-false columns, grant individual members, then enable each TEAM policy. Rollback: disable the namespace policy first; already published versions remain published. No web toggle is included in this API-only slice.
