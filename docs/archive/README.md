# SkillHub 项目档案

## 目的

`docs/archive/` 是 SkillHub 的长期项目档案库。

它用于保存：

- 项目的整体生命周期与关键里程碑
- 与项目相关的内部和外部发文
- 重要产品与工程决策背后的上下文
- 核心贡献者之间的交流记录或摘要
- 能支持后续文档编写的来源证据

这个档案库有意与 `docs/` 下已有的产品、架构和面向用户的文档分开。现有设计文档和交付文档仍然是实现层面的主记录，而档案库负责补充历史背景、交叉链接和证据链。

## 信息模型

档案采用三层 Markdown 结构：

1. 叙事层：讲述项目故事和主要阶段。
2. 索引层：组织里程碑、时间线、贡献者、发文与 GitHub 记录。
3. 来源层：保存链接、摘要、截图、提交引用、聊天摘要等证据。

## 目录说明

- `_meta/`：分类法、维护规则、脱敏策略、档案变更记录
- `inbox/`：尚未归类材料的临时落点
- `timeline/`：按年和月份组织的时间记录
- `milestones/`：带有背景和影响说明的重要项目节点
- `narratives/`：综合整理后的项目叙事
- `decisions/`：重要决策的档案化摘要
- `contributors/`：核心贡献者画像与贡献线索
- `communications/`：会议纪要、聊天摘要、访谈摘要
- `publications/`：内部和外部的文章、公告、提及记录
- `github/`：经过整理的 issue、PR 和 release 追踪
- `sources/`：证据记录、链接、截图、提交摘要
- `templates/`：新建档案条目时可复用的 Markdown 模板

## 推荐工作流

1. 先把原始链接、截图、笔记或引用材料放进 `inbox/`。
2. 为后续可能引用的材料创建或更新 `sources/` 条目。
3. 把事件补入对应月份的 `timeline/` 文件。
4. 当上下文足够丰富时，再整理进 `milestones/`、`narratives/`、`contributors/` 或 `decisions/`。

## 命名规则

- 时间线文件：`YYYY/YYYY-MM.md`
- 里程碑文件：`YYYY-MM-DD-short-topic.md`
- 决策文件：`YYYY-MM-DD-short-decision.md`
- 发文文件：`YYYY-MM-DD-channel-short-title.md`
- 贡献者文件：`name.md`
- 来源文件：`YYYY-MM-DD-source-type-short-topic.md`

## 初始入口

以下文档适合作为第一轮档案整理的起点：

- [产品方向](../00-product-direction.md)
- [系统架构](../01-system-architecture.md)
- [交付路线图](../10-delivery-roadmap.md)
- [Issue 自动化设计](../2026-04-08-issue-automation-design.md)
- [第 1 阶段计划](../superpowers/plans/2026-03-11-phase1-foundation-auth.md)
- [第 2 阶段设计](../superpowers/specs/2026-03-12-phase2-namespace-skill-core-design.md)

## 当前状态

这套档案框架已经完成第一版播种，但按设计它仍然是不完整的。随着项目演进，后续很可能会出现新的板块、新的文档类型，以及更细的索引方式。

当前信号最强的历史记录：

- [仓库历程叙事](./narratives/repository-history.md)
- [仓库提交历史来源](./sources/commits/2026-04-25-repository-commit-history.md)
