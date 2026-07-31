---
name: generate-skillhub-weekly-report
description: Generate or revise concise SkillHub weekly open-source reports in formal Chinese Markdown and self-contained tabbed HTML for GitHub Pages. Use for weekly summaries of repository key metrics, feature iteration, ecosystem progress, project health, releases, promotion activities, and value-ranked Issues/PRs; also use when GitHub access is partial and the report must be assembled from API results, cached exports, local Git history, or maintainer notes.
---

# Generate SkillHub Weekly Report

Generate a report whose overview can be understood in three minutes. Treat Markdown as the content source and self-contained HTML as its public presentation.

## Default Invocation

When the user only says “使用 Skill 生成本周周报”:

- display the reporting period as the previous Thursday through the current Thursday in `Asia/Shanghai`;
- use explicit cutoff timestamps for event filtering: after the previous Thursday cutoff through the current Thursday cutoff. Treat the previous cutoff as exclusive and the current cutoff as inclusive so an event is counted once across adjacent reports;
- when invoked on Friday or later, use the most recently completed Thursday cutoff; when invoked on Thursday before the report is released, end at the explicit snapshot and label it as partial;
- when migrating an existing archive from another cadence, keep the requested Thursday—Thursday label. If no previous Thursday cutoff exists, begin at the previous Thursday 00:00 and disclose any overlap with an older report in the data Tab; do not shorten the visible reporting period into a transition snapshot;
- use `iflytek/skillhub` as the source repository unless the user names another repository;
- discover an existing sibling or configured `skillhub-weekly` site repository and reuse its latest report, theme, manifest, and build scripts;
- collect repository facts automatically and continue when promotion links are absent; do not stop at a draft;
- generate the report, update `reports.json`, rebuild the archive, validate, and render-check locally.

When the user says “生成并发布本周周报”, additionally commit the weekly-site changes, push its configured publishing branch, monitor the Pages workflow, and verify the live report URL. When the SkillHub source repository contains a configured `weekly/` mirror, also synchronize the canonical site sources into that mirror, validate them, submit a source-repository PR, and verify the official mirror after merge. Do not push directly to the SkillHub default branch.

## Workflow

1. Resolve the previous-Thursday—current-Thursday `Asia/Shanghai` reporting label and the exact cutoff timestamps. Ask only when the user requests a non-default period or the boundary is ambiguous.
2. Read [references/metrics.md](references/metrics.md) for data semantics.
3. Read [references/report-structure.md](references/report-structure.md) for the overview and Tab structure.
4. Collect available facts with source timestamps. If GitHub is inaccessible, continue with cached data, local Git, release pages, or maintainer notes; mark missing data instead of using zero.
5. Separate weekly facts, current snapshot, and post-period progress.
6. Select decision-relevant overview information:
   - 4–6 repository metrics;
   - 3–5 feature iterations;
   - 3–5 ecosystem developments;
   - at most 3 next-week priorities.
7. Add compact evidence visuals when the underlying data is verified:
   - overview: repository scale and weekly collaboration flow;
   - ecosystem: independent participation and adoption signals;
   - health: Actions outcome composition, Issue/PR age, and `needs-info` proportion.
8. Put workflow details and current A/B value queues in separate Tabs. Do not repeat them in the overview.
9. Add maintainer-provided articles, talks, community sharing, and partner projects under ecosystem progress.
10. Generate Markdown, then fill [assets/weekly-report-template.html](assets/weekly-report-template.html). Replace every `{{PLACEHOLDER}}`, including repository name, source summary, overall status, and all four metric values and labels.
11. When publishing through a weekly-site repository:
    - write the canonical report to `site/reports/<YYYY-Www>/index.html`;
    - append its metadata to `site/reports.json` and update `latest`;
    - run the repository's theme sync, build, and validation scripts so the home and archive pages stay aligned.
    - when a SkillHub source checkout with `weekly/` is available, mirror `site/`, `assets/`, and `scripts/` byte-for-byte into `weekly/`; never copy `_site/`;
    - rebuild and validate from the SkillHub checkout, then submit a normal PR that links the weekly-report tracking Issue;
    - preserve one authoritative content source: edit the weekly-site repository first and treat the SkillHub copy as a reviewed mirror.
12. Run:

```bash
python3 .agents/skills/generate-skillhub-weekly-report/scripts/validate_report.py <report.md>
python3 .agents/skills/generate-skillhub-weekly-report/scripts/validate_report.py <report.html>
```

13. Render HTML at desktop and narrow widths when a browser is available. Verify mouse, keyboard, direct-hash, no-JavaScript, and print behavior.

## Official SkillHub Mirror

When the SkillHub source repository contains `weekly/`, treat it as a reviewed
mirror of the standalone weekly-site repository:

1. Resolve both repository roots and confirm both worktrees are clean enough to
   isolate the report changes. Preserve unrelated and untracked files.
2. Update and validate the standalone site first.
3. Create a fresh SkillHub branch from the latest `origin/main`; do not reuse a
   stale report branch.
4. Synchronize these trees exactly, including upstream deletions:
   - standalone `site/` to SkillHub `weekly/site/`;
   - standalone `assets/` to SkillHub `weekly/assets/`;
   - standalone `scripts/` to SkillHub `weekly/scripts/`.
5. Do not copy `_site/`, a standalone Pages workflow, or repository credentials.
6. Update `weekly/source.json` to the exact standalone commit and verify the
   mirrored trees byte-for-byte before committing.
7. Build and validate from `weekly/`, then build VitePress and place the weekly
   output under `.vitepress/dist/weekly/`.
8. Submit a normal SkillHub PR linked to the tracking Issue. After merge,
   monitor the existing `Deploy Docs` workflow and verify:
   - `https://iflytek.github.io/skillhub/weekly/`;
   - `https://iflytek.github.io/skillhub/weekly/archive.html`;
   - the current report route under `/skillhub/weekly/reports/<YYYY-Www>/`.

Do not wrap the report in a VitePress page or restyle it for the official
mirror. The report HTML, inlined Notion-light CSS, charts, Tabs, and responsive
behavior must remain identical to the standalone site.

## Default HTML

Use two required Tabs and up to two optional Tabs:

1. **总览**（required）：keep to about 2–3 desktop screens and use these modules when they have content:
   - **仓库关键信息**：Stars/Forks 规模与变化、本周 Issue/PR 流转图、Release、CI 和一句风险判断。
   - **功能迭代信息**：已完成、推进中、期后完成、待设计的 3–5 个重要事项。
   - **生态相关进展**：社区参与信号、Fork/下载、文档或集成成果、传播与合作动作。
2. **项目健康**（optional）：health cards plus engineering, security pipeline, release, backlog age, and community response evidence.
3. **Issue 与 PR**（optional）：weekly flow plus current A/B value queues; collapse cleanup candidates.
4. **数据说明**（required）：time boundaries, sources, missing permissions, and post-period semantics.

End with no more than three “下周关注” items. Keep statistical definitions in the data Tab.

## Module Rules

- Wrap every independently optional content block in a complete element with `data-module="<stable-name>"`.
- Omit an entire module when it has no verified content. Never emit an empty heading, empty table, or placeholder row.
- Keep `repository-summary` required. Treat feature, ecosystem, promotion, response, workflow, and queue modules as optional.
- Treat each chart as an optional submodule. Omit its title, container, legend, and axis when its source data is missing.
- If a detail panel has no modules, omit both its Tab button and its panel.
- Keep “未取得” only when the data gap itself affects interpretation; do not use it to preserve an otherwise empty module.

## Full Governance Mode

Only when the user explicitly requests a full backlog or governance appendix:

- rank full queues A—D;
- place complete Issue/PR tables in appendices.

Do not put all 21/19 open items into the normal weekly report.

## Rules

- Explain a metric once. Do not repeat KPI grids across sections.
- Keep headline metrics to four and use tables or lists instead of card grids.
- Keep post-period progress beside the affected feature or Release and exclude it from weekly totals.
- Do not infer “no vulnerabilities” from successful security workflows.
- Do not infer weekly Star growth from a single snapshot.
- Do not turn forks, downloads, Issue authors, and PR authors into a funnel without cohort evidence.
- Preserve links for Releases, material Issues/PRs, and promotion actions.
- Use the bundled Notion-light theme: white canvas, warm-gray sections, near-black text, light borders, and one blue interaction accent.
- Use formal, direct language. Avoid slogans, decorative charts, and long methodology prose.
- Give every chart primitive (`.bars`, `.segbar`, `.donut`, `.ecosystem-signals`) `role="img"` and a complete numeric `aria-label`; also keep visible numeric labels beside the graphic.
- Use accessible `button` Tabs with matching `aria-controls`/`aria-labelledby`, arrow-key navigation, direct hashes, and a no-JavaScript fallback.
- Keep report HTML self-contained. Do not load external CSS, JavaScript, images, or fonts.

## Completion Gate

Finish only when:

- the overview contains the required repository module and any available optional modules in order;
- two to four Tabs are present, with matching panels and keyboard access;
- no empty module or panel is emitted;
- feature iteration contains concrete links and status;
- ecosystem includes both participation and delivered progress;
- verified repository flow and ecosystem signals are visualized without implying a funnel;
- available Actions and backlog distributions have accessible charts;
- Issue/PR detail is value-ranked without dumping the full backlog;
- no more than three next-week priorities remain;
- missing data is explicit;
- the weekly-site home, archive, and report routes build successfully when a site repository is used;
- a configured SkillHub mirror has source parity, passes its own build, and is
  assembled into the existing VitePress Pages artifact;
- validation passes.
