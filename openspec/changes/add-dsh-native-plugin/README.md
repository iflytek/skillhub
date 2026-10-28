# SkillHub 原生 DSH 插件：规格阅读入口

本文档集定义 SkillHub 原生 DSH 插件的产品需求、交互、架构、接口、实现与验收。

## 本期产品

在 DSH 安装一个原生 SkillHub 插件，连接一个支持匿名读取的实例。既能通过技能广场浏览和管理，也能在原对话搜索、打开详情抽屉和明确请求安装。无需用户另装 SkillHub CLI；HTTP API 获取内容，插件 Host 内置的共享 CLI 核心负责文件操作。

| 入口 | 本期能力 | 详情容器 |
| --- | --- | --- |
| 技能广场 | 浏览、搜索、安装、已安装、检查更新、升级、卸载、使用已安装技能 | 主内容区子页面，左上返回 |
| 原 DSH 对话 | 搜索卡片、详情、显式安装、自定义结果卡片、使用已安装技能 | 抽屉，窄屏内容区浮层 |
| 宿主插件配置 | 单一 Registry 地址，版本/审核/评论 auto/show/hide | 原生插件配置区域 |

不做登录、Token、私有访问、权限产品、DSH 插件市场、自动更新、降级、强制覆盖、发表评论或自动执行技能。对话升级/卸载工具不在本期，管理操作从广场进入。

## 文档职责与阅读顺序

| 文档 | 唯一负责的内容 |
| --- | --- |
| [proposal.md](proposal.md) | 范围与决策摘要 |
| [product-design.md](product-design.md) | 页面、功能 ID、视觉和产品行为 |
| [use-skill-design.md](use-skill-design.md) | 使用此技能：本地核验、原生输入框衔接、固定案例文件与生命周期兼容约定 |
| [skill-examples-design.md](skill-examples-design.md) | 固定案例文件的 JSON 协议、版本分发、跨端生命周期与兼容策略 |
| [skill-examples-execution-plan.md](skill-examples-execution-plan.md) | 本轮开源范围、实施阶段、验收及后续SaaS同步边界 |
| [skill-examples-saas-agent-prompt.md](skill-examples-saas-agent-prompt.md) | 可交给SaaS Agent的案例文件增量适配任务 |
| [conversation-design.md](conversation-design.md) | 对话卡片/抽屉、工具参数、选择引用、历史结果 |
| [engineering-blueprint.md](engineering-blueprint.md) | 详细工程方案：装配、工具/UI、核心复用、构建和实施切片 |
| [design.md](design.md) | 总体架构、模块边界、复用依据 |
| [interface-design.md](interface-design.md) | 已有 HTTP API、拟议 Remote DTO、错误映射 |
| [lifecycle-design.md](lifecycle-design.md) | 安装/升级/卸载、锁、提交、日志与恢复的唯一规则 |
| [dsh-integration-design.md](dsh-integration-design.md) | 官方源码基线、原生加载/通信/生命周期 |
| [implementation-design.md](implementation-design.md) | 文件职责、改造接缝与阶段依赖 |
| [specs/](specs/dsh-native-experience/spec.md) | SHALL 需求及 WHEN/THEN 验收场景；其余两项见下 |
| [消费规范](specs/dsh-skill-consumption/spec.md)、[连接规范](specs/dsh-registry-connection/spec.md) | 消费与连接的正式验收约束 |
| [acceptance.md](acceptance.md) | 功能到证据的追踪、阶段出入条件和未决阻塞 |
| [compatibility-design.md](compatibility-design.md) | 首版单一 DSH 基线、宿主适配及平台测试 |
| [release-guide.md](release-guide.md) | 验证、环境部署、插件发布与回退 |
| [release-notes.md](release-notes.md) | 当前开发候选的功能变化、使用限制及发布前缺口 |
| [registry-api-contract.md](registry-api-contract.md) | Registry 公共契约与开源/SaaS 同步 |
| [saas-agent-prompt.md](saas-agent-prompt.md) | 可直接交给 SaaS 研发 Agent 的适配任务、边界与验收要求 |
| [tasks.md](tasks.md)、[verification.md](verification.md) | 任务进度、当前验证结果与发布缺口 |

## 文档使用规则

正式需求及验收场景由 specs 定义；产品、架构和接口分册提供实现细则。接口字段以 interface-design.md 为准，文件事务与恢复规则以 lifecycle-design.md 为准，对话选择引用以 conversation-design.md 为准。

需求变更应同步相关设计、任务和验收项。实施阶段、兼容条件及验收标准见 [acceptance.md](acceptance.md)；检查和评审记录单独保存在 [verification.md](verification.md)。


## 推荐阅读路径

- 产品评审：产品设计 → 对话设计 → 验收矩阵。
- 开发实施：详细实现方案 → 接口设计 → 生命周期设计 → 任务清单。
- API 同步与交付：Registry 契约 → 验证、部署与发布方案。

首期功能范围以本目录规范为准。当前研发基线为 DSH 0.2.0-rc.2，包名为 `@astron-team/skillhub-dsh`；正式平台支持与发布决定按验收门禁执行。过程日志、截图批次和临时研究材料在仓库外留档，仓库仅保留正式规格、验证摘要及可复用测试。

## 后续增量：安装级技能启停（设计阶段）

[安装级启停设计](skill-toggle-design.md)定义受管理安装的停用、恢复、更新与卸载联动；正式验收见消费规范，执行拆分见 tasks.md 第44节。此增量尚未实现，不属于已发布0.5.0的能力。

- [使用优先页面与二级安装管理](skill-use-first-design.md)：日常动作、只读安装选择及精确管理边界。
