# SkillHub Tabbed Weekly Report

## Information Architecture

Use a self-contained HTML report with two required Tabs (`总览`, `数据说明`) and two optional Tabs (`项目健康`, `Issue 与 PR`).

### Shared Header

Use one conclusion sentence and exactly four headline metrics. Put the current Star total and its valid comparison in the first metric. If the period-start Star snapshot is missing, write `周增量未取得`; if only a partial comparison exists, show both timestamps and mark it as non-natural-week data.

### Tab 1：总览

Keep this Tab to about 2–3 desktop screens and 800–1,500 Chinese characters.

#### 一、仓库关键信息（必选）

Use two compact visual blocks:

- **规模快照**：current Stars and Forks plus valid weekly or timestamped changes;
- **协作流转**：horizontal bars for weekly Issue created/closed and PR created/merged/closed-unmerged.

Mention Actions, Release, and current open Issue/PR counts in one concise judgment under the visuals. End with one risk sentence. Do not expose workflow-by-workflow detail here. Do not draw a Star trend from fewer than two comparable snapshots.

#### 二、功能迭代信息（有内容时）

Show 3–5 important items, ordered by user value:

| Status | Item | Weekly progress | Next step or post-period result |
|---|---|---|---|

Use statuses such as `已完成`, `推进中`, `期后完成`, `待设计`, and `受阻`. Include related Issue, PR, and Release links.

Do not turn every Issue or PR into a feature item.

#### 三、生态相关进展（有内容时）

Combine:

- unique community participants;
- merged community contributions;
- documentation and Agent integration results;
- weekly Forks and CLI downloads;
- maintainer-provided articles, talks, sharing, and partner projects.

When verified, begin with three independent signal tiles: contributor participation, weekly Forks, and CLI downloads. State that the signals do not form a funnel. Follow with a short list of delivered integrations, documentation, promotion, and cooperation, with at most five rows.

#### 下周关注

Use no more than three actions. Each action needs a concrete object and observable result.

### Tab 2：项目健康（有模块时）

Include concise evidence for:

- health cards for engineering stability, security, automation, Release, Issue governance, and PR governance;
- an Actions segmented bar for success, failure, authorization wait, skipped, and cancelled outcomes;
- security pipeline execution and authorization gaps;
- release delivery and post-period releases;
- horizontal age bars for open Issue and PR;
- a donut only when a meaningful numerator and denominator exist, such as `needs-info / open Issue`;
- automatic versus maintainer response.

Keep workflow breakdowns in one table. Successful security workflows mean pipeline stability, not zero vulnerabilities.

### Tab 3：Issue 与 PR（有模块时）

Show:

1. weekly created/closed/merged flow;
2. weekly new Issues and PRs ordered by value;
3. current A and B priority queues;
4. one collapsed cleanup block for superseded or already-implemented PRs.

Only include the complete A—D backlog when full governance mode is explicitly requested.

### Tab 4：数据说明（必选）

Keep timezone, snapshot, source coverage, missing permissions, formulas, and the meaning of post-period data here. Do not repeat methodology in other Tabs.

## Editorial Rules

- Use short, formal Chinese sentences.
- Keep the overview around 800–1,500 Chinese characters.
- Limit headline metrics to four.
- Limit feature and ecosystem rows to five each.
- Do not add a separate execution summary, promotion chapter, or appendix to the overview.
- Label `未取得`, `无法计算`, and `期后` precisely.
- Do not repeat the same fact in multiple sections.

## HTML Rules

- Use the bundled Notion-light theme: white canvas, warm-gray bands, near-black text, light borders, and one blue interaction accent.
- Use one paper-like content column. Reserve cards for headline metrics, health judgments, and evidence charts.
- Use tables for linked iteration and queue details; use charts only for composition or distribution questions.
- Give `.bars`, `.segbar`, `.donut`, and `.ecosystem-signals` `role="img"`, a complete numeric `aria-label`, and adjacent visible values.
- Wrap each optional chart in its own `data-module`; omit the complete chart when source values are missing.
- Keep one HTML file with inline CSS and no external assets.
- Implement Tabs with buttons, ARIA relationships, arrow keys, and URL hashes.
- Mark each optional block with `data-module`; omit empty modules and omit empty detail Tabs with their panels.
- Show every panel without JavaScript and when printing.
- Support keyboard focus, horizontal Tab scrolling, narrow screens, reduced motion, and A4 print.
