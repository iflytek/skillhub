---
title: 安装使用
sidebar_position: 2
description: 安装和使用技能
---

# 安装使用

## 通过 CLI 安装

### 安装最新版本

```bash
clawhub install @team/my-skill
```

### 安装指定版本

```bash
clawhub install @team/my-skill@1.2.0
```

### 按标签安装

```bash
clawhub install @team/my-skill@beta
```

### 使用 ClawHub CLI 安装

```bash
clawhub install my-skill
clawhub install team-name--my-skill
```

## 安装目录

按以下优先级安装：

| 优先级 | 路径 | 说明 |
|--------|------|------|
| 1 | `./.agent/skills/` | 项目级，universal 模式 |
| 2 | `~/.agent/skills/` | 全局级，universal 模式 |
| 3 | `./.claude/skills/` | 项目级，Claude 默认 |
| 4 | `~/.claude/skills/` | 全局级，Claude 默认 |

## 在 Claude Code 中使用

安装后，技能会被 Claude Code 自动发现和加载。

## 下一步

- [评分与收藏](./ratings) - 反馈和收藏技能
