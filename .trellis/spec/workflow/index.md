# Workflow Guidelines

This layer explains how SkillHub chooses between direct edits, Trellis tasks, and OpenSpec changes.
These shared conventions are readable without installing Trellis. Local execution tools are optional.
Respect an existing user choice about task creation; do not repeatedly ask or silently create a task.

## Pre-Development Checklist

- Product capability or scope changes: read [Product Direction](../../../docs/00-product-direction.md); preserve registry-first positioning and compatibility requirements.
- Read `.trellis/spec/index.md` for the unified routing map.
- Follow [Bounded Context Reading](./context-reading.md) for document delivery and resumption.
- Read [Grill With Docs](./grill-with-docs.md) when the requirement, domain language, or design
  boundary is still fuzzy.
- Read [OpenSpec Workflow](./openspec.md) for large requirements.
- Read [Development Flow](./development-flow.md) for task-size routing and verification expectations.
- For implementation work, also read the relevant domain index selected by the root routing map, including CLI when applicable.

## Quality Check

- Does this task have the right container: direct edit, Trellis task, or OpenSpec change?
- If the requirement was fuzzy, did `$grill-with-docs` settle the decision tree before planning?
- If an OpenSpec change exists, did implementation stay aligned with its proposal/spec/design/tasks?
- Did verification read [Testing and Quality](../testing/index.md) and the relevant domain checks?
- Did runtime/release work read [Deployment](../deployment/index.md) before executing commands?

## Task History

Finish/cleanup and cross-worktree history lookup follow [Local Task Archive](./task-archive.md).
When no record exists for this task, do not create one merely to archive it. Preserve unrelated task records.
