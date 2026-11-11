# OpenSpec Workflow

OpenSpec remains the formal workflow for large SkillHub requirements.

Authoritative source:

- `openspec/`
- Existing example: `openspec/changes/add-skill-suites/`

## When to Use OpenSpec

Use or create `openspec/changes/<change-id>/` for large requirements or architecture decisions, such as:

- A substantial new product capability or large user-facing workflow.
- Architecture or module-boundary change.
- Breaking or broad public API, persistence, security, or lifecycle changes requiring coordinated design and acceptance.

An ordinary multi-step feature uses task records; changing one endpoint or adding one test does not
automatically require a new OpenSpec change. If an accepted OpenSpec already owns the affected behavior,
update that source rather than creating a competing contract. Preserve the user's existing task-creation choice.

## Requirement clarification

When requirements, domain terms or design boundaries remain materially unclear, the Agent
first follows [the clarification workflow](./grill-with-docs.md) and uses the installed
`grill-with-docs` entry without asking the user to activate it. Reuse settled answers; clear
small edits do not need an interview. Keep formal outcomes in the current OpenSpec artifacts,
respect the native propose planning boundary, and do not restart Trellis formal planning.

## Expected Artifacts

Each large change should have:

- `proposal.md`: why the change exists and what problem it solves.
- `specs/<capability>/spec.md`: behavior deltas and acceptance scenarios.
- `design.md`: technical approach and important trade-offs.
- `tasks.md`: implementation and verification checklist.

## Trellis Relationship

Trellis tasks may link to an OpenSpec change. Trellis should not replace OpenSpec for formal
requirements. If task work changes accepted behavior, update the OpenSpec source first.

## Stable Agent Integration

OpenSpec owns propose/apply/verify/archive. Trellis supplies engineering conventions,
optional context injection and local continuation records. An existing OpenSpec change does
not require a second Trellis PRD, design, implementation plan, or formal task checklist.
Use the task locations returned by the active OpenSpec schema; do not assume a fixed file
name or interpret planning-artifact completion as implementation acceptance.

The default execution is the main session. Spawn a child only when delegation is explicitly
selected. Reading conventions does not require a child Agent, local Trellis installation,
hook availability, or creation of a task record for a small edit.

Entry preparation is the Agent's responsibility, following
[the development flow](./development-flow.md). A new task/session or a missing entry may
need the existing safe sync/setup; the user does not have to type those commands. Do not
turn optional local tooling into a prerequisite for reading the public engineering rules.

### apply

Before a dependent implementation edit:

1. Read `AGENTS.md`, [the spec index](../index.md), and this workflow document. Read
   `.trellis/local-workflow.md` when present for local execution configuration.
2. Run `openspec status --change <id> --json` and
   `openspec instructions apply --change <id> --json` for the intended change. Read the
   returned project `context` and every current artifact under `contextFiles`; do not
   substitute a copied planning package. Respect the schema's blocked state and existing
   user scope. Artifact completion or checked tasks alone is not an acceptance verdict.
3. Select domain indexes and topic documents from the request and actual affected source.
   Read their **bodies**. Include additional layers when an API, generated consumer,
   permission, persistence behavior, or other cross-layer contract is affected.
4. For a design-system-dependent page/component, read the actual selected theme values,
   used component contracts and page/interaction rules selected by the frontend index.
   Missing private design resources block dependent design work, not unrelated work.
5. Briefly record the selected paths and reasons in the existing execution record or
   conversation. Reuse `implement.jsonl`/`check.jsonl` when a Trellis task exists, with
   matching applicable topics; no second formal requirement or task list is created.
6. Treat missing, truncated and non-inlined bodies as unresolved. Follow [bounded context reading](./context-reading.md), one page per tool result; obtain required missing material before relying on it.
   Continue read-only investigation or unrelated authorized work where useful.

Then implement against the OpenSpec artifacts and these conventions. Update only the
OpenSpec task source returned by the CLI when a task's work and stated verification are
actually satisfied. Run impact-based checks; preserve failures and unverified boundaries.

`trellis-before-dev` may organize this procedure when available; it does not own a separate
copy of the conventions. An index, path, hash, marker or script exit code alone is not
evidence that the corresponding body reached the Agent.

### verify

Verification is **report-oriented**. Do not repair product code, run apply actions, mark
implementation tasks complete, or archive a change as part of verification.

1. Read this policy, current project `context`, the intended OpenSpec artifacts and the
   engineering topic bodies used for implementation. The native verify workflow reuses
   `instructions apply` to obtain context: do not execute its implementation guidance or
   treat `all_done` as a verification verdict. There is no `operations.verify` setting.
2. Inspect the complete authorized diff, including relevant untracked files. Compare
   the actual affected domains with the prepared context; add omitted applicable topics
   and [testing guidance](../testing/index.md). Re-read changed or missing context.
3. Check requirements and scenarios against source and behavior, and review engineering
   compliance against the same rule set. Check proposed design values against the actual
   component/theme sources, not a claimed design-system label.
4. Run relevant verification commands after inspecting their effects. Tests may create
   normal test artifacts; do not change product code, reset data, or start/stop unrelated
   services to make verification pass. Missing dependencies are reported as not verified.
5. Report passed, failed, not verified, and not applicable separately, with source paths,
   commands, affected revision/diff, and relevant limitations. A summary or task checkbox
   is not source evidence. A build is not browser/business acceptance or deployment proof.
6. Return findings for repair in apply and verify again after repair. Existing
   `trellis-check` permits mechanical fixes, so reuse its **read-only review and check
   selection** here rather than invoking its full self-fix procedure.

### Recovery and child context

On a new session, resumption, context compaction, or relevant source/impact change, obtain
the current change association and necessary bodies again. A transcript or previous hash
does not establish that the current Agent still has the required full text. Follow [bounded context reading](./context-reading.md):
reuse the task-scoped source list, reload necessary current bodies, and audit actual post-compaction
tool results when the transcript is available. Missing pages remain unresolved, even when a prior check passed.

When explicitly delegating, supply the intended change/task and applicable source paths.
Use the matching native hook only when the actual thread has loaded and trusted it;
otherwise the child reads the supplied sources. Do not borrow another session's active
task or assume the parent transcript was inherited. Configuration inspection, manual hook
execution and a real host event are different evidence levels.

After code changes, reassess which prior checks still cover the current implementation.
Rerun affected checks rather than reusing an old success as evidence for changed behavior.
Keep diagnostic and context receipts separate from claims of implementation compliance.

## Installation and Update Checks

The optional Codex entry generator uses the installed OpenSpec 1.17.0 CLI with an isolated
temporary profile, leaving the user's global profile unchanged. It installs upstream
workflows plus a thin apply/verify reference to this policy; generated Skills remain local.

From the repository root:

```bash
python3 scripts/setup-agent-workflow.py          # preview only
python3 scripts/setup-agent-workflow.py --apply  # generate/update unmodified managed files
python3 scripts/setup-agent-workflow.py --check  # detect missing or changed entries
```

The tool does not install dependencies or alter hook trust, requirements, task state,
AGENTS, or other worktrees. Review local conflicts instead of forcing an overwrite.
After refreshing/upgrading upstream instruction files, run this setup and check again;
plain `openspec update` can replace the project adaptation or deselect optional verify.
Integrity checks prove entry configuration only, not body delivery or model compliance.

Public rules and the explicit-reading procedure remain usable without optional local
automation.
