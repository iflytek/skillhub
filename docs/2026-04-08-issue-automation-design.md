# Issue 自动分诊 MVP 设计

## 目标

通过自动将 GitHub issue 分诊到三个队列中，降低维护者负担：

- `triage/deferred`：低优先级 issue，会随着时间推移逐步上浮
- `triage/core`：高优先级或高风险 issue，需要 core maintainer 接手
- `triage/agent-ready`：高优先级、低风险 issue，适合作为后续 agent 执行候选

本 MVP 版本还不会自动修复 issue。它聚焦在评分、路由、打标签，以及让
backlog 持续流动。

当前版本支持两种执行模式：

- 仅规则分诊
- 规则 + 兼容 OpenAI 的 LLM 辅助

## 为什么这样拆分

最初的方案把优先级和执行难度混在同一个决策里。实践上，如果把它们拆开，
系统会更容易调参：

- `Priority`：这个 issue 现在是否值得投入时间？
- `Route`：一旦值得处理，应该由谁来接手？

这样一来，高价值但高难度的 issue 仍然可以保持高优先级，同时继续路由到
`triage/core`。

## 输入

自动化会读取 issue 的实时标题、正文、标签、评论和时间戳。

结构化的 issue 表单字段来自：

- [bug_report.yml](../.github/ISSUE_TEMPLATE/bug_report.yml)
- [feature_request.yml](../.github/ISSUE_TEMPLATE/feature_request.yml)
- [reward-task.yml](../.github/ISSUE_TEMPLATE/reward-task.yml)

## 评分模型

每个 issue 会沿四个维度评分：

- `impact`（1-5）：对用户和工作流的影响
- `urgency`（1-5）：发布时间压力、功能损坏情况或重复讨论程度
- `effort`（1-5）：预估改动规模和协作成本
- `confidence`（1-5）：issue 描述的完整性和可执行程度

优先级计算公式如下：

```text
priority = impact * 0.45 + urgency * 0.35 + age_boost + engagement_boost
```

其中：

- `age_boost`：基于 SLA 的升级机制
  - 第 7-9 天：预热阶段，最低提升到 `priority/p2`
  - 第 10-13 天：强制移出 `triage/deferred`，最低提升到 `priority/p1`
  - 第 14 天及以后：在下一次 triage/rescore 时，将该 issue 视为已违反 SLA，
    并至少提升到 `priority/p0`
- `engagement_boost`：由评论压力和奖励金额共同决定，上限为 +1.0

在 MVP 中，`effort` 不会直接降低优先级，它只影响路由。

## LLM 辅助分诊

配置后，工作流可以调用兼容 OpenAI 的 chat completions API。

LLM 不会替代规则引擎。它只用于辅助：

- 生成 issue 摘要
- 对软性分数做微调
- 生成 `needs-info` 的追问问题
- 为维护者提供更好的判断依据
- 为 `triage/core` 生成 maintainer 交接摘要

硬性门槛仍然由规则控制：

- 缺失必填信息
- auth、schema、migration、SDK 或公共契约变更等高风险区域
- 最终是否可以提升到 `triage/agent-ready`

issue 正文和评论都视为不可信输入。工作流会：

- 在发送给模型前截断过长的正文和评论
- 明确告诉模型，issue 文本是数据而不是指令
- 使用严格的 JSON 协议校验模型输出
- 如果 provider 调用失败或 JSON 校验失败，则回退到仅规则模式

### 模式

- `off`：仅规则
- `shadow`：调用 LLM 并展示其建议，但最终仍沿用仅规则的路由和标签
- `assist`：允许 LLM 对软性分数做最多 `+/-1` 的微调，然后重新应用硬性门槛

### 何时使用 LLM

工作流只会在 issue 看起来存在歧义或价值较高时调用 LLM，例如：

- `triage/needs-info`
- `triage/core`
- 靠近路由阈值的 issue
- 低置信度案例
- 正文很长或讨论很多的 issue
- 需要更多判断的 feature 或 reward issue

## 路由规则

1. `triage/needs-info`
   当缺少必填字段或 `confidence <= 2` 时触发。

2. `triage/deferred`
   当 `priority < 3.6`、issue 不受信息缺失阻塞、且 issue 年龄仍低于 SLA
   升级底线时触发。

3. `triage/core`
   当 `priority >= 3.6` 且满足以下任一条件时触发：
   - issue 阻塞了 OpenClaw/ClawHub 核心工作流，例如 install、publish、
     update、sync 或基于 namespace 的发布
   - `effort >= 4`
   - `confidence <= 3`
   - 存在高风险关键词或会影响契约的字段

4. `triage/agent-ready`
   当 `priority >= 3.6`、`effort <= 3`、`confidence >= 4`，且不存在高风险
   信号时触发。

在 `assist` 模式下，LLM 建议可以对 `impact`、`urgency`、`effort` 和
`confidence` 各自最多调整 1 分。规则引擎随后会重新计算优先级和路由。

涉及 OpenClaw/ClawHub 核心工作流的 issue 是进入 `triage/core` 的硬性门槛；
LLM 辅助不会放宽这一规则。

## 受管标签

自动化负责管理以下标签前缀：

- `triage/`
- `priority/`
- `effort/`
- `risk/`

当前使用的具体标签有：

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

其余所有标签都保持不变。

另外，自动化还识别一个不由其管理的人工操作标签：

- `triage-manual`：冻结该 issue 的自动分诊更新

## 工作流

### 1. Issue 分诊

文件：[issue-triage.yml](../.github/workflows/issue-triage.yml)

触发条件：

- `issues.opened`
- `issues.edited`
- `issues.reopened`
- 当评论包含 `/retriage` 时触发 `issue_comment.created`
- `workflow_dispatch`

执行动作：

- 拉取 issue 和评论
- 计算分数和路由
- 更新或创建受管标签
- 更新或创建一条分诊评论，其中同时包含人类可读的判断理由和隐藏的机器状态
- 可选调用兼容 OpenAI 的 provider，并合并结果

### 2. Deferred Backlog 重新评分

文件：
[issue-backlog-rescore.yml](../.github/workflows/issue-backlog-rescore.yml)

触发条件：

- 每 6 小时一次
- `workflow_dispatch`

执行动作：

- 列出所有带有 `triage/deferred` 标签的 open issue
- 结合年龄和参与度加成重新计算优先级
- 决定将每个 issue 升级还是保留
- 原地更新分诊评论
- 当 issue 内容未变化时复用缓存的 LLM 结果

试运行说明：

- 当前定时 rescore 只扫描 `triage/deferred` 队列中的 issue
- 这可以保证低优先级 backlog 不会在 `deferred` 中闲置超过第 10 天
- 一旦某个 issue 已经从 `deferred` 中升级出去，之后第 14 天的进一步升级
  依赖新的 triage 事件或手动 `/retriage`
- 在试运行阶段，14 天规则应被视为运营层面的 SLA 目标，而不是仓库范围内的
  硬性计时器

## 脚本

新的 GitHub 自动化脚本位于
[`.github/scripts`](/Users/wowo/workspace/skillhub/.github/scripts)：

- [github.ts](/Users/wowo/workspace/skillhub/.github/scripts/github.ts)：精简版
  GitHub REST 客户端
- [issue-triage-config.ts](/Users/wowo/workspace/skillhub/.github/scripts/issue-triage-config.ts)：
  标签、阈值和关键词规则
- [issue-llm-config.ts](/Users/wowo/workspace/skillhub/.github/scripts/issue-llm-config.ts)：
  LLM 模式、环境变量和调用启发式
- [issue-llm-provider.ts](/Users/wowo/workspace/skillhub/.github/scripts/issue-llm-provider.ts)：
  兼容 OpenAI 的 chat completions 客户端
- [issue-llm-evaluator.ts](/Users/wowo/workspace/skillhub/.github/scripts/issue-llm-evaluator.ts)：
  prompt 构造、JSON 校验和缓存 key 生成
- [issue-triage-lib.ts](/Users/wowo/workspace/skillhub/.github/scripts/issue-triage-lib.ts)：
  解析、评分、路由和评论渲染
- [issue-triage-merge.ts](/Users/wowo/workspace/skillhub/.github/scripts/issue-triage-merge.ts)：
  有界合并和硬性门槛重应用
- [issue-triage.ts](/Users/wowo/workspace/skillhub/.github/scripts/issue-triage.ts)：
  单 issue 入口
- [issue-backlog-rescore.ts](/Users/wowo/workspace/skillhub/.github/scripts/issue-backlog-rescore.ts)：
  deferred 队列重新评分入口

## 配置

设置以下 GitHub 仓库变量和 secret，即可启用 LLM 辅助分诊：

仓库变量：

- `ISSUE_TRIAGE_LLM_MODE`
- `ISSUE_TRIAGE_LLM_BASE_URL`
- `ISSUE_TRIAGE_LLM_MODEL`
- `ISSUE_TRIAGE_LLM_TIMEOUT_MS` 可选
- `ISSUE_TRIAGE_LLM_TEMPERATURE` 可选
- `ISSUE_TRIAGE_LLM_MAX_COMMENTS` 可选
- `ISSUE_TRIAGE_LLM_MAX_COMMENT_CHARS` 可选
- `ISSUE_TRIAGE_LLM_MAX_BODY_CHARS` 可选

仓库 secret：

- `ISSUE_TRIAGE_LLM_API_KEY`

建议的第一轮上线方式：

- `ISSUE_TRIAGE_LLM_MODE=shadow`
- 先观察几天分诊评论
- 等 LLM 建议看起来稳定后，再切换到 `assist`

兼容 OpenAI 的变量示例：

```text
ISSUE_TRIAGE_LLM_MODE=shadow
ISSUE_TRIAGE_LLM_BASE_URL=https://your-provider.example.com/v1
ISSUE_TRIAGE_LLM_MODEL=gpt-4.1-mini
```

## 推出计划

### Phase 1：当前阶段

- 启用 triage 和 backlog rescore
- 观察几周的 issue 流量后微调阈值
- 允许维护者通过 `triage-manual` 冻结特定 issue 的自动化处理
- 如果使用 LLM，从 `shadow` 模式开始

### Phase 2：Maintainer 交接

为 `triage/core` issue 增加 issue-brief 生成器，输出内容包括：

- 复现提示
- 可能涉及的模块
- 风险备注
- 验证清单

这些输出可以直接用于本地编程 agent 会话，以及现有的并行 worktree 流程。

当前 MVP 已经会在 `triage/core` issue 的分诊评论中直接嵌入一个
`Maintainer Brief` 区块。该摘要包括：

- 简洁的 issue 摘要
- issue 为什么被升级到 core
- 复现路径或操作路径备注
- 疑似相关模块或工作流负责人
- 风险提示
- 验证清单

### Phase 3：自托管 Issue Agent

增加一个自托管 runner，监听 `triage/agent-ready`，并执行：

- 创建隔离的分支和 worktree
- 运行解决 issue 的 agent
- 执行最小相关测试集
- 打开一个 draft PR

在这个阶段，以下场景仍应保留硬性阻断：

- auth 和权限变更
- 安全敏感变更
- schema 或 migration 相关工作
- 公共 API、SDK 或 CLI 契约变更

## 待调优问题

- 参与度加成是否只看评论数就够了，还是也应该拉取 reactions
- reward issue 是否应比当前 MVP 获得更强的价值加成
- `agent-ready` 是否应要求 `effort <= 2`，而不是 `<= 3`
- 某些区域（如 `scanner`）是否应默认视为高风险
- 某些团队是否应长期保持 `shadow` 模式，只把 `assist` 用在更窄的仓库子集上
