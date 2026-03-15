---
title: 创建技能包
sidebar_position: 1
description: 学习如何创建符合规范的技能包
---

# 创建技能包

## 技能包结构

一个标准的 SkillHub 技能包结构如下：

```
my-skill/
├── SKILL.md              # 主入口文件（必需）
├── references/           # 参考资料（可选）
├── scripts/              # 脚本（可选）
└── assets/               # 静态资源（可选）
```

## SKILL.md 格式

SKILL.md 是技能包的主入口文件，使用 YAML frontmatter + Markdown 正文格式：

```markdown
---
name: my-skill
description: 一句话描述这个技能的用途
x-astron-category: code-review
---

# 技能说明

这里是技能的详细说明...
```

### Frontmatter 字段

| 字段 | 必需 | 说明 |
|------|------|------|
| `name` | 是 | 技能标识，kebab-case 格式 |
| `description` | 是 | 技能简短描述 |
| `x-astron-category` | 否 | 分类标签 |
| `x-astron-runtime` | 否 | 运行时要求 |
| `x-astron-min-version` | 否 | 最低版本要求 |

## 文件限制

- 单文件大小：最大 1MB
- 总包大小：最大 10MB
- 文件数量：最多 100 个
- 允许的文件类型：`.md`, `.txt`, `.json`, `.yaml`, `.yml`, `.js`, `.ts`, `.py`, `.sh`, `.png`, `.jpg`, `.svg`

## 下一步

- [发布流程](./publish) - 发布技能包
