# 档案分类法

## 文档类型

frontmatter 中使用一个主 `type`：

- `timeline`：按时间顺序记录事件
- `milestone`：带有背景和影响说明的重要项目节点
- `narrative`：跨多个事件综合整理出的叙事
- `decision`：关键决策的档案化摘要
- `publication`：内部或外部的发文记录
- `communication`：会议纪要、聊天摘要或访谈摘要
- `contributor`：贡献者画像或贡献线索记录
- `source`：为其他档案条目提供支撑的证据记录
- `index`：导航页
- `template`：可复用的条目模板

## 保密级别

使用一个 `confidentiality` 值：

- `public`：可以在团队外公开发布
- `internal`：面向仓库贡献者和维护者
- `restricted`：包含敏感的贡献者、产品或运维上下文

如果拿不准，默认使用 `internal`。

## 来源类型

来源记录中的 `source_type` 使用以下值之一：

- `commit`
- `pull-request`
- `issue`
- `release`
- `article`
- `announcement`
- `meeting-note`
- `chat-summary`
- `interview`
- `screenshot`
- `demo`
- `document`

## 推荐标签

使用简短标签帮助交叉链接：

- product
- architecture
- auth
- namespace
- publishing
- search
- governance
- docs
- automation
- release
- contributor
- external-coverage
- internal-communication

## 必备 Frontmatter 字段

所有非模板类条目都应包含：

```yaml
---
id: unique-id
type: source
status: draft
date: 2026-04-25
updated: 2026-04-25
confidentiality: internal
tags: []
---
```

## 可选 Frontmatter 字段

以下字段在有需要时补充：

- `people`
- `teams`
- `source_type`
- `source_url`
- `source_path`
- `related_docs`
- `related_prs`
- `related_issues`
- `related_commits`
- `related_sources`
- `summary`

## 交叉引用规则

- 把 `sources/` 视为证据层。
- 把 `timeline/`、`milestones/`、`decisions/` 和 `narratives/` 视为摘要层。
- 优先使用链接，而不是重复复制正文。
- 当某个文件提出历史性判断时，至少补上一条支持它的来源或仓库文档链接。
