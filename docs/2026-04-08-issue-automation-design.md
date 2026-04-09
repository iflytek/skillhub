# Issue Automation MVP Design

## Goal

Reduce maintainer load by automatically triaging GitHub issues into three
queues:

- `triage/deferred`: low-priority issues that should age upward over time
- `triage/core`: high-priority or high-risk issues that need core maintainer
  ownership
- `triage/agent-ready`: high-priority, low-risk issues that are candidates for
  future agent execution

The MVP does not auto-fix issues yet. It focuses on scoring, routing, labeling,
and keeping the backlog fresh.

This version supports two execution modes:

- rules-only triage
- rules + OpenAI-compatible LLM assistance

## Why This Split

The initial proposal mixed priority and execution difficulty into one decision.
In practice, the system is easier to tune if it separates:

- Priority: should we spend time on this issue now?
- Route: who should handle the issue once it is worth doing?

This lets a high-value but difficult issue stay high priority while still
routing to `triage/core`.

## Inputs

The automation reads the live issue title, body, labels, comments, and
timestamps.

Structured issue form fields are parsed from:

- [bug_report.yml](../.github/ISSUE_TEMPLATE/bug_report.yml)
- [feature_request.yml](../.github/ISSUE_TEMPLATE/feature_request.yml)
- [reward-task.yml](../.github/ISSUE_TEMPLATE/reward-task.yml)

## Scoring Model

Each issue is scored across four axes:

- `impact` (1-5): user and workflow impact
- `urgency` (1-5): release pressure, breakage, or repeated discussion
- `effort` (1-5): estimated change size and coordination cost
- `confidence` (1-5): how complete and actionable the issue description is

Priority is computed from:

```text
priority = impact * 0.45 + urgency * 0.35 + age_boost + engagement_boost
```

Where:

- `age_boost`: SLA-driven escalation
  - day 7-9: warm-up, minimum `priority/p2`
  - day 10-13: forced out of `triage/deferred`, minimum `priority/p1`
  - day 14+: on the next triage/rescore pass, treat the issue as SLA-breached
    and raise it to at least `priority/p0`
- `engagement_boost`: comment pressure plus reward amount, capped at +1.0

Effort does not directly lower priority in the MVP. It only affects routing.

## LLM-Assisted Triage

When configured, the workflow can call an OpenAI-compatible chat completions
API.

The LLM does not replace the rule engine. It only helps with:

- issue summaries
- soft score adjustments
- `needs-info` follow-up questions
- better rationale for maintainers
- `triage/core` maintainer handoff briefs

Hard gates stay in rules:

- missing required information
- high-risk areas like auth, schema, migration, SDK, or public contract changes
- final promotion into `triage/agent-ready`

The issue body and comments are treated as untrusted input. The workflow:

- truncates long bodies and comments before sending them to the model
- tells the model to treat issue text as data, not instructions
- validates the model output against a strict JSON contract
- falls back to rules-only if the provider call or JSON validation fails

### Modes

- `off`: rules-only
- `shadow`: call the LLM, show its recommendation, but keep the rule-only route
  and labels
- `assist`: let the LLM nudge soft scores by at most `+/-1`, then re-apply hard
  gates

### When the LLM is used

The workflow only calls the LLM for issues that look ambiguous or high-value,
such as:

- `triage/needs-info`
- `triage/core`
- issues near the routing threshold
- low-confidence cases
- long issue descriptions or heavy discussion
- feature or reward issues that need more judgment

## Routing Rules

1. `triage/needs-info` Trigger when required fields are missing or
   `confidence <= 2`.

2. `triage/deferred` Trigger when `priority < 3.6`, the issue is not blocked on
   missing information, and the issue age is still below the SLA escalation
   floor.

3. `triage/core` Trigger when `priority >= 3.6` and any of the following are
   true:
   - the issue blocks an OpenClaw/ClawHub core workflow such as install,
     publish, update, sync, or namespace-based publishing
   - `effort >= 4`
   - `confidence <= 3`
   - high-risk keywords or contract-impact fields are present

4. `triage/agent-ready` Trigger when `priority >= 3.6`, `effort <= 3`,
   `confidence >= 4`, and no high-risk signals are present.

In `assist` mode, LLM suggestions can nudge `impact`, `urgency`, `effort`, and
`confidence` by at most one point. The rule engine then recomputes priority and
route.

OpenClaw/ClawHub core workflow issues are a hard gate to `triage/core`; LLM
assistance does not relax that rule.

## Managed Labels

The automation owns these label prefixes:

- `triage/`
- `priority/`
- `effort/`
- `risk/`

Current concrete labels:

- `triage/needs-info`
- `triage/deferred`
- `triage/core`
- `triage/agent-ready`
- `priority/p0`
- `priority/p1`
- `priority/p2`
- `priority/p3`
- `effort/s`
- `effort/m`
- `effort/l`
- `risk/high`

All other labels remain untouched.

Separately, the automation recognizes one non-managed operator label:

- `triage-manual`: freeze automated triage updates for that issue

## Workflows

### 1. Issue Triage

File: [issue-triage.yml](../.github/workflows/issue-triage.yml)

Triggers:

- `issues.opened`
- `issues.edited`
- `issues.reopened`
- `issue_comment.created` when the comment contains `/retriage`
- `workflow_dispatch`

Actions:

- fetch issue and comments
- compute scores and route
- upsert managed labels
- upsert a single triage comment containing both human-readable reasoning and
  hidden machine state
- optionally call the OpenAI-compatible provider and merge the result

### 2. Deferred Backlog Rescore

File:
[issue-backlog-rescore.yml](../.github/workflows/issue-backlog-rescore.yml)

Triggers:

- every 6 hours
- `workflow_dispatch`

Actions:

- list all open issues labeled `triage/deferred`
- recompute priority with age and engagement boosts
- promote or keep each issue
- update the triage comment in place
- reuse cached LLM results when the issue content has not changed

Trial-run note:

- the scheduled rescore currently scans `triage/deferred` issues only
- this guarantees low-priority backlog will not sit idle in `deferred` past day
  10
- once an issue has already been promoted out of `deferred`, any later day-14
  escalation depends on a new triage event or a manual `/retriage`
- during trial run, the 14-day rule should be treated as an operational SLA
  target, not yet as a repo-wide hard timer

## Scripts

New GitHub automation scripts live under
[`.github/scripts`](/Users/wowo/workspace/skillhub/.github/scripts):

- [github.ts](/Users/wowo/workspace/skillhub/.github/scripts/github.ts): minimal
  GitHub REST client
- [issue-triage-config.ts](/Users/wowo/workspace/skillhub/.github/scripts/issue-triage-config.ts):
  labels, thresholds, and keyword rules
- [issue-llm-config.ts](/Users/wowo/workspace/skillhub/.github/scripts/issue-llm-config.ts):
  LLM mode, environment variables, and call heuristics
- [issue-llm-provider.ts](/Users/wowo/workspace/skillhub/.github/scripts/issue-llm-provider.ts):
  OpenAI-compatible chat completions client
- [issue-llm-evaluator.ts](/Users/wowo/workspace/skillhub/.github/scripts/issue-llm-evaluator.ts):
  prompt construction, JSON validation, and cache key generation
- [issue-triage-lib.ts](/Users/wowo/workspace/skillhub/.github/scripts/issue-triage-lib.ts):
  parsing, scoring, routing, and comment rendering
- [issue-triage-merge.ts](/Users/wowo/workspace/skillhub/.github/scripts/issue-triage-merge.ts):
  bounded merge and hard-gate re-application
- [issue-triage.ts](/Users/wowo/workspace/skillhub/.github/scripts/issue-triage.ts):
  single-issue entrypoint
- [issue-backlog-rescore.ts](/Users/wowo/workspace/skillhub/.github/scripts/issue-backlog-rescore.ts):
  deferred queue rescoring entrypoint

## Configuration

Set these GitHub repository variables and secrets to enable LLM-assisted triage:

Repository variables:

- `ISSUE_TRIAGE_LLM_MODE`
- `ISSUE_TRIAGE_LLM_BASE_URL`
- `ISSUE_TRIAGE_LLM_MODEL`
- `ISSUE_TRIAGE_LLM_TIMEOUT_MS` optional
- `ISSUE_TRIAGE_LLM_TEMPERATURE` optional
- `ISSUE_TRIAGE_LLM_MAX_COMMENTS` optional
- `ISSUE_TRIAGE_LLM_MAX_COMMENT_CHARS` optional
- `ISSUE_TRIAGE_LLM_MAX_BODY_CHARS` optional

Repository secret:

- `ISSUE_TRIAGE_LLM_API_KEY`

Recommended first rollout:

- `ISSUE_TRIAGE_LLM_MODE=shadow`
- watch the triage comments for a few days
- switch to `assist` once the LLM suggestions look stable

Example OpenAI-compatible variable set:

```text
ISSUE_TRIAGE_LLM_MODE=shadow
ISSUE_TRIAGE_LLM_BASE_URL=https://your-provider.example.com/v1
ISSUE_TRIAGE_LLM_MODEL=gpt-4.1-mini
```

## Rollout Plan

### Phase 1: Now

- enable triage and backlog rescore
- tune thresholds by observing a few weeks of issue traffic
- let maintainers freeze automation on specific issues via `triage-manual`
- if using an LLM, start in `shadow` mode

### Phase 2: Maintainer Handoff

Add an issue-brief generator for `triage/core` issues that prepares:

- reproduction hints
- likely modules
- risk notes
- validation checklist

This output can feed local programming-agent sessions and the existing parallel
worktree flow.

The current MVP now embeds a `Maintainer Brief` section directly into the triage
comment for `triage/core` issues. That brief includes:

- a concise issue summary
- why the issue was escalated to core
- reproduction or operator path notes
- suspected modules or workflow owners
- risk callouts
- a validation checklist

### Phase 3: Self-Hosted Issue Agent

Add a self-hosted runner that listens for `triage/agent-ready` and:

- creates an isolated branch and worktree
- runs the issue-solving agent
- executes the smallest relevant test set
- opens a draft PR

This phase should keep hard blockers in place for:

- auth and permission changes
- security-sensitive changes
- schema or migration work
- public API, SDK, or CLI contract changes

## Open Tuning Questions

- Whether comment count alone is enough for engagement boost, or if reactions
  should also be fetched
- Whether reward issues should receive a stronger value boost than the current
  MVP gives them
- Whether `agent-ready` should require `effort <= 2` instead of `<= 3`
- Whether certain areas like `scanner` should be considered high-risk by default
- Whether some teams should keep `shadow` mode permanently and reserve `assist`
  for a narrower repository subset
