# 治理 X/Twitter 工作流 Skill

本示例说明团队如何用 SkillHub 分发经过审核的 X/Twitter 工作流 Skill，
并将实际执行保留在 OpenClaw 或其他兼容的智能体运行时中。

SkillHub 负责存储、扫描、版本管理和审核 Skill 包。SkillHub 不执行
TweetClaw 动作，也不为运行时保存 Xquik 凭证。

## 适用场景

当团队需要复用以下流程时，可以使用这个模式：

- 搜索 tweets 和搜索 tweet replies
- 导出 followers 和查询用户
- 媒体工作流审核
- tweet monitors 和 webhook 交接
- 经过审批的 post tweets、post tweet replies、direct messages 或 giveaway draws

如果流程包含内部策略、客户上下文或团队专属审核步骤，请使用 private 或
namespace-only Skill。

## 包结构

创建一个普通的 SkillHub Skill 包。运行时凭证不要放进包内，示例中只使用占位符。

```text
tweetclaw-social-workflow/
├── SKILL.md
└── references/
    └── review-checklist.md
```

`SKILL.md` 示例：

```md
---
name: tweetclaw-social-workflow
description: Review and run X/Twitter workflows with TweetClaw through OpenClaw.
---

# TweetClaw Social Workflow

Use this skill when the operator needs reviewed X/Twitter work through
TweetClaw and OpenClaw.

## Runtime requirements

- OpenClaw has the TweetClaw plugin installed:
  `openclaw plugins install @xquik/tweetclaw`
- The runtime has `XQUIK_API_KEY` configured outside the skill package.
- The operator approves every write, direct message, media upload, monitor,
  webhook, or giveaway draw before execution.

## Read-first flow

1. Start with search tweets, search tweet replies, follower export, or user
   lookup.
2. Summarize source tweet IDs, account IDs, URLs, and review notes.
3. Ask for approval before calling any TweetClaw write or account-changing
   action.
```

`references/review-checklist.md` 示例：

```md
# Review Checklist

- No API keys, cookies, access tokens, or account credentials are present.
- Public examples use placeholder account IDs and tweet IDs.
- Read workflows are separated from write workflows.
- Post tweets, replies, DMs, media upload, monitors, webhooks, and giveaway
  draws require operator approval.
- The skill says that OpenClaw executes TweetClaw, not SkillHub.
```

## 发布到命名空间

当流程需要命名空间管理员先审核，再让其他用户安装时，请发布到团队命名空间。

```bash
skillhub publish ./tweetclaw-social-workflow \
  --namespace social-ops \
  --visibility namespace-only
```

如果使用 ClawHub 兼容路径，请保持 canonical slug 与 SkillHub 的命名空间映射一致：

```bash
npx clawhub publish ./tweetclaw-social-workflow \
  --slug social-ops--tweetclaw-social-workflow \
  --version 1.0.0
```

SkillHub 会把 `social-ops--tweetclaw-social-workflow` 映射为命名空间
`social-ops` 和 Skill slug `tweetclaw-social-workflow`。

## 审核和扫描

发布后，Skill Scanner 会检查包内容；如果启用了命名空间审核，SkillHub 会创建审核任务。

审核者应检查：

- Skill Scanner 报告
- `SKILL.md` 中的运行时要求
- review checklist
- 示例中只出现占位符
- write、DM、media、monitor、webhook 和 giveaway workflows 都需要审批

完整流程见[安全扫描](/guide/scanner)和[审核与治理](/guide/review)。

## 安装和运行

将审核通过的 Skill 安装到 OpenClaw 兼容的 Skill 目录：

```bash
skillhub install tweetclaw-social-workflow \
  --namespace social-ops \
  --agent openclaw
```

然后在 SkillHub 之外配置运行时：

```bash
openclaw plugins install @xquik/tweetclaw
export XQUIK_API_KEY=REPLACE_WITH_RUNTIME_SECRET
```

安装后的 Skill 可以引导操作者完成经过审核的 TweetClaw 工作流，而 SkillHub
仍然作为受治理的分发和审计层。
