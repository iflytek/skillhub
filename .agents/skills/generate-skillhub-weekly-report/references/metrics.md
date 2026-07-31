# SkillHub Weekly Metrics

## Source Priority

Use the highest available source and retain retrieval timestamps:

1. Authenticated GitHub REST API or GitHub CLI output.
2. Public GitHub REST API and release pages.
3. Saved JSON/CSV snapshots from the reporting period.
4. Maintainer-provided Issue/PR notes and promotion links.
5. Local Git history and repository workflow files.

If a higher-priority source is inaccessible, continue with the next source. State the gap and never substitute zero.

## Time Boundaries

- Reporting label: previous Thursday through current Thursday in `Asia/Shanghai`.
- Event period: after the previous Thursday cutoff through the current Thursday cutoff, expressed as `(previous_cutoff, current_cutoff]`. Store both exact timestamps so boundary-day events are counted once across adjacent reports.
- If no previous Thursday cutoff exists, use the previous Thursday 00:00 as the fallback start and state that fallback in the data Tab.
- API period: convert both endpoints to UTC before filtering timestamps.
- Current snapshot: capture a single explicit timestamp after the reporting period.
- Thursday invocation before the report cutoff: end at the explicit snapshot and label the cycle as partial.
- Post-period progress: events after the current Thursday cutoff. Report them beside the relevant item and exclude them from weekly totals.
- Cadence migration: retain the requested previous-Thursday—current-Thursday label. If the fallback start overlaps an older Monday—Sunday report, disclose the overlapping dates rather than shortening the new report. Subsequent reports must use the prior cutoff timestamp to avoid duplicate events.

Use `created_at` for inflow, `closed_at` for closures, `merged_at` for merges, and `published_at` for Releases.

## GitHub Semantics

The GitHub REST Issues endpoint returns both Issues and pull requests. Identify PR-shaped entries by the presence of the `pull_request` key, or use the Pull Requests endpoint for PR calculations. Do not use repository `open_issues_count` as an Issue-only count.

Relevant read endpoints:

| Data | REST path | Notes |
|---|---|---|
| Repository snapshot | `/repos/{owner}/{repo}` | Stars, forks, subscribers/watchers and repository metadata |
| Issues | `/repos/{owner}/{repo}/issues` | Includes PRs; paginate and separate them |
| Pull requests | `/repos/{owner}/{repo}/pulls` | Use detailed PR reads for additions, deletions, changed files and merge state |
| Releases | `/repos/{owner}/{repo}/releases` | Filter by `published_at`; distinguish app and `cli-*` tags |
| Workflow runs | `/repos/{owner}/{repo}/actions/runs` | Paginate and filter by `created_at` |
| Forks | `/repos/{owner}/{repo}/forks` | Paginate before filtering by creation time |
| Traffic | `/repos/{owner}/{repo}/traffic/*` | May require additional repository access; record unavailable responses |

Use a page size up to 100 and follow pagination until the time boundary is safely crossed. Do not rely on the first page.

## Default Overview Selection

The overview does not display every available metric. Select only:

- current Stars and Forks;
- weekly Actions effective success rate;
- weekly app/CLI Release result;
- weekly Issue created/closed;
- weekly PR created/merged/closed;
- weekly Forks or CLI downloads when they materially describe ecosystem activity.

Visualize only verified relationships:

- repository scale: current Stars/Forks with valid timestamped changes;
- collaboration flow: Issue created/closed and PR created/merged/closed-unmerged;
- ecosystem signals: authors, merged contributors, Forks, and downloads as separate signals, never as a funnel.

Put Actions composition, queue age, security details, and response latency in the corresponding detail Tab. Do not repeat them in the overview.

## Default Detail Tabs

Use the normal HTML report to expose concise detail without turning the overview into a dashboard:

- **项目健康**: one health judgment table, one workflow table, release/security notes, age bands, and response evidence;
- **Issue 与 PR**: weekly flow, weekly value ranking, current A/B priorities, and a collapsed cleanup list;
- **数据说明**: time boundaries, formulas, source coverage, permissions, and missing data.

Reserve complete A—D queues and every open item for explicit full governance mode.

## Health Dimensions

### Engineering Stability

Report:

- total Actions records;
- actually executed runs;
- successes and failures;
- `action_required`;
- conditional `skipped`;
- effective success rate;
- failure concentration by workflow or PR;
- recovery evidence.

Calculate:

```text
effective_success_rate = success / (success + failure)
```

Exclude `action_required` and conditional `skipped` from the denominator, but display their counts because they indicate coverage gaps.

### Security and Supply Chain

Report:

- executed Security runs and failures;
- runs awaiting authorization;
- presence of scheduled CodeQL/dependency review controls when verified from workflow files;
- public security issues or supply-chain proposals.

Successful runs mean the pipeline executed successfully. They do not prove that open vulnerability counts are zero.

### Automation Operations

Report actual execution and success for backlog rescoring, Issue triage, documentation deployment, rewards, or other governance automation. Separate intentional skips from failures.

### Release Delivery

Report:

- app Releases in the period;
- CLI Releases in the period;
- latest release before or within the period;
- release gap at period end;
- post-period Releases, explicitly excluded from weekly totals;
- whether the “one Release per week” goal was met or skipped with reason.

### Issue Governance

Report:

- created and closed during the period;
- net change;
- current open count;
- priority and triage-label structure;
- age bands: `<7`, `7—13`, `14—29`, `≥30` days;
- `needs-info` count and proportion;
- observable human response coverage.

### PR Governance

Report:

- created, merged, and closed without merge during the period;
- open-count change;
- current open count;
- age bands;
- stale, superseded, already-implemented, blocked, and ready-to-merge groups;
- merge Lead Time only with sample size and limitation.

### Community Response

Separate:

1. automatic triage latency;
2. first maintainer acknowledgement;
3. first executable disposition or paired PR;
4. final closure.

Automatic comments do not count as maintainer acknowledgement.

## Promotion Metrics

Track actions and outcomes separately:

| Action fields | Outcome examples |
|---|---|
| Type, title, channel/partner, date, URL | Reads, views, interactions, clicks, referred visits, integrations |

Also report weekly forks, npm CLI downloads, and timestamped repository snapshots. Do not infer weekly Star growth without a period-start snapshot. Do not claim an action caused a change without attribution evidence.

## Ecosystem Metrics

Include:

- unique new Issue authors and author association;
- unique new PR authors;
- merged community contributors;
- documentation contributions;
- Agent integrations or partner projects;
- recurring demand themes;
- community-feedback-to-delivery closures;
- product-boundary risks.

Forks, downloads, Issue authors, PR authors, and merged contributors represent different participation signals. They are not a conversion funnel unless the same cohort is linked across stages.

## Value Ranking

Rank weekly flow and current backlog separately:

- **A**: immediate maintenance value; material user impact with clear evidence or mature implementation.
- **B**: high value but requires design, security review, decomposition, or a clear gate.
- **C**: conditional, limited-scope, or appropriate for incremental work.
- **D**: defer, transfer, supersede, or close.

Use user impact, product fit, security/operational risk, evidence quality, implementation maturity, and maintenance cost. Explain the judgment; do not rank by label alone.
