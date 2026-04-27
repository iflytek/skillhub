---
id: source-2026-04-25-repository-commit-history
type: source
status: curated
date: 2026-04-25
updated: 2026-04-25
confidentiality: internal
source_type: commit
source_path: git history at HEAD
people: [vsxd, yun-zhi-ztl, dongmucat, XiaoSeS, wowo-zZ, xiose, wowo]
tags: [source, commits, timeline, contributors]
related_docs:
  - docs/archive/narratives/repository-history.md
  - docs/archive/timeline/2026/2026-03.md
  - docs/archive/timeline/2026/2026-04.md
related_prs: []
related_issues: []
related_commits: []
related_sources: []
summary: 覆盖 2026-03-11 到 2026-04-24 的第一阶段仓库提交历史来源记录。
---

# 仓库提交历史

## 范围

这份来源记录汇总了 `2026-04-25` 时 `HEAD` 可达的完整 Git 历史。

- 提交总数：`734`
- 非合并提交：`624`
- 合并提交：`110`
- 可见时间范围：`2026-03-11` 到 `2026-04-24`

## 活跃度快照

- `2026-03`：`632` 个提交
- `2026-04`：`102` 个提交

当前历史中最密集的提交日为：

- `2026-03-12`：`89` 个提交
- `2026-03-13`：`70` 个提交
- `2026-03-15`：`69` 个提交
- `2026-03-19`：`67` 个提交
- `2026-03-14`：`60` 个提交

这说明项目的启动阶段被压缩得非常厉害：设计、实现、工作流搭建和产品成形，几乎都在前十天内集中落地。

## 贡献者分布

以 `HEAD` 为准，非合并提交数量最高的贡献者为：

- `vsxd`：`208`
- `yun-zhi-ztl`：`194`
- `dongmucat`：`68`
- `XiaoSeS`：`31`
- `wowo-zZ`：`30`
- `xiose`：`27`
- `FenjuFu`：`12`
- `Xudong Sun`：`10`
- `wowo`：`9`

早期提交流高度集中在 `vsxd` 和 `yun-zhi-ztl` 身上，随后逐渐扩展成更分布式的多人协作格局。

## 提交前缀扫描

以下统计来自对非合并提交标题前缀的扫描：

- `fix`：`267`
- `feat`：`163`
- `docs`：`57`
- `test`：`33`
- `refactor`：`17`
- `chore`：`25`

这个比例强烈说明，仓库从最初搭建阶段很快就进入了稳定性修复和产品硬化阶段。

## 主题扫描

按关键字扫描非合并提交标题后，最显眼的重复主题如下：

- `web_ui`：`141`
- `skill`：`119`
- `auth`：`84`
- `review_governance`：`77`
- `docs`：`72`
- `search`：`46`
- `storage_io`：`38`
- `compat_cli`：`27`
- `scanner_security`：`16`
- `notification`：`5`

这和仓库中可见的产品形态是对得上的：提交历史并不只是后端导向，而是很快扩展到 UI、治理、文档、兼容性和运维体验。

## 发布与协作标记

提交标题中可见的发布标记包括：

- `2026-03-13`：`chore(release): cut v0.1.0-beta.2`
- `2026-03-13`：`chore(release): v0.1.0-beta.7`
- `2026-03-19`：`chore(release): v0.1.0`

可见的 PR 合并标记从 `2026-03-13` 开始，一直持续到 `2026-04-24`。这表明项目很早就从“自举式实现”转向了更明确的 PR 协作节奏。

## 提交流中的强里程碑锚点

- `2026-03-11`：初始提交以及项目更名为 SkillHub
- `2026-03-12`：Phase 计划，以及后端、前端、审核、CLI、兼容层、管理端和运维的大量工作在同一天集中落下
- `2026-03-15` 到 `2026-03-20`：生命周期管理、命名空间治理、安全加固、语义搜索、文档站点、资料审核和标签系统相关工作
- `2026-03-19`：`v0.1.0`
- `2026-03-23` 到 `2026-03-31`：通知、扫描系统、文件浏览侧栏、发布部署扩展、分享能力与 Unicode slug
- `2026-04-01` 到 `2026-04-24`：双语文档站点、部署指南、PR e2e、issue 自动化、发布警告确认、密码重置、GitLab OAuth，以及反复出现的搜索/存储/审核硬化

## 方法

这份记录基于 shell 对仓库历史的扫描整理而成，所用命令大致如下：

```bash
git rev-list --count HEAD
git rev-list --count --no-merges HEAD
git rev-list --count --merges HEAD
git shortlog -sn --no-merges HEAD
git log --reverse --date=short --pretty=format:'%ad|%h|%an|%s'
git log --date=short --pretty=format:'%ad' | cut -c1-7 | sort | uniq -c
```

这份文件是更高层项目历程叙事背后的证据层。
