---
title: 安装使用
sidebar_position: 2
description: 安装和使用技能
---

# 安装使用

## 通过 CLI 安装

SkillHub 兼容 ClawHub CLI 协议，你可以直接使用 `clawhub` 命令行工具来连接你私有部署的 SkillHub 并安装技能。

### 配置注册中心与登录

在使用前，你需要将 CLI 指向你私有部署的 SkillHub 实例，并使用在 Web 控制台创建的 API Token 进行登录：

```bash
# 配置注册中心地址（指向你们私有部署的 SkillHub，默认是 8080 端口）
export CLAWHUB_REGISTRY=http://localhost:8080

# 使用 Token 登录
npx clawhub login --token '你的_API_TOKEN'

# 确认当前登录身份
npx clawhub whoami
```

如果出现提示或者想在单次命令中指定注册中心，也可以使用 `--registry` 参数：

```bash
npx clawhub install my-skill --registry http://localhost:8080
```

### 安装技能

支持多种方式安装不同版本或标签的技能：

```bash
# 安装最新版本
npx clawhub install @team/my-skill

# 安装指定版本
npx clawhub install @team/my-skill@1.2.0

# 按标签安装
npx clawhub install @team/my-skill@beta
```

如果使用原生的 ClawHub CLI 格式，遇到命名空间时，会使用 `--` 分隔命名空间和技能名：

```bash
# 安装全局命名空间下的技能
npx clawhub install my-skill

# 安装特定团队命名空间下的技能
npx clawhub install team-name--my-skill
```

## 技能查看 (Inspect)

在安装前，你可以使用 `inspect` 命令查看技能的详细信息、版本历史和依赖关系：

```bash
npx clawhub inspect ifly-pdf-image-ocr
npx clawhub inspect ocr-team--ifly-pdf-image-ocr
```

## 安装目录

按以下优先级安装：

| 优先级 | 路径 | 说明 |
|--------|------|------|
| 1 | `./.agent/skills/` | 项目级，universal 模式 |
| 2 | `~/.agent/skills/` | 全局级，universal 模式 |
| 3 | `./.claude/skills/` | 项目级，Claude 默认 |
| 4 | `~/.claude/skills/` | 全局级，Claude 默认 |

## 多 AI Agent 支持

发布到 SkillHub 的技能，不只是在一个 AI 应用上能用。目前已支持 Claude Code、OpenClaw、AstronClaw、Loomy、astron-agent 等多个 AI Agent 平台。

以 Claude Code 或 Codex 为例，安装后，技能会被自动发现和加载。你可以直接在提示词中调用它：
> 提示词：把这份文件做 ocr，输出 markdown

一次发布，各端复用。技能不再需要在每个平台上单独维护一份。

## 下一步

- [评分与收藏](./ratings) - 反馈和收藏技能
