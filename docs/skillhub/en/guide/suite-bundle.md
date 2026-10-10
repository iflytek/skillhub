# Suite Bundle Import

A Suite Bundle uploads one ZIP or folder to create a Suite or create a new version from a published base. SkillHub first shows the member diff. Member publication and review start only after explicit confirmation.

## Archive format

The archive root must contain exactly one `SUITE.yaml`. Every packaged member has its own directory with a root `SKILL.md`. Undeclared skills, overlapping directories, unsafe or duplicate paths, and configured file limits block preview.

```yaml
apiVersion: skillhub.iflytek.com/v1alpha1
kind: SkillSuiteBundle
metadata:
  namespace: global
  slug: clinical-workflow
spec:
  mode: CREATE
  version: 1.0.0
  displayName: Clinical workflow
  summary: Validate intake data and produce a summary
  overview: The entry Skill validates and dispatches input; the summary Skill produces the result.
  visibility: PUBLIC
  entry: "@global/intake"
  members:
    - skill: "@global/intake"
      package:
        path: skills/intake
        visibility: PUBLIC
    - skill: "@global/shared-dictionary"
      reference:
        version: 2.3.1
```

Use exactly one source per member: `package` publishes the uploaded Skill directory; `reference` pins an existing accessible `PUBLISHED` version without copying or changing it. Creating or updating a packaged Skill requires the normal Namespace and lifecycle permissions.

## Create, update, and review

Use `mode: CREATE` for a new coordinate. For `mode: UPDATE`, set `baseVersion` and describe the complete desired member order. Preview reports `ADDED`, `UPDATED`, `UNCHANGED`, and `REMOVED`; order and Entry changes count as updates, while a completely unchanged update cannot be confirmed.

Choose local import from the Suite create or new-version page, upload once, inspect the preview, acknowledge warnings per affected member and removals separately, then confirm. Preview creates no SkillVersion, review task, or SuiteVersion. Confirmation requires an `Idempotency-Key` and rechecks permissions, versions, references, and staged hashes. A Suite draft is created only after every required member completes its normal publication and review workflow.

## Deployment flags

Confirmation is disabled by default. Enable both server-side write paths for the complete workflow:

```bash
SKILLHUB_SUITE_BUNDLE_CONFIRMATION_ENABLED=true
SKILLHUB_SUITE_REVIEW_WRITES_ENABLED=true
```

The same protocol works against a self-hosted Registry and does not depend on a SaaS endpoint.
