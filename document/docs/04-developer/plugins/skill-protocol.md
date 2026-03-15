---
title: 技能协议
sidebar_position: 1
description: SKILL.md 规范和技能包协议
---

# 技能协议

## SKILL.md 规范

### 基本格式

```markdown
---
name: my-skill
description: When to use this skill
---

# Markdown 正文

技能指令内容...
```

### 必需字段

| 字段 | 说明 |
|------|------|
| `name` | 技能标识，kebab-case |
| `description` | 技能简短描述 |

### 扩展字段

| 字段 | 说明 |
|------|------|
| `x-astron-category` | 分类标签 |
| `x-astron-runtime` | 运行时要求 |
| `x-astron-min-version` | 最低版本要求 |

## 技能包结构

```
my-skill/
├── SKILL.md              # 主入口文件（必需）
├── references/           # 参考资料（可选）
├── scripts/              # 脚本（可选）
└── assets/               # 静态资源（可选）
```

## 文件校验

- 根目录必须包含 `SKILL.md`
- 文件类型白名单
- 单文件大小限制：1MB
- 总包大小限制：10MB
- 文件数量限制：100 个

## 客户端安装目录

按以下优先级安装：

1. `./.agent/skills/`
2. `~/.agent/skills/`
3. `./.claude/skills/`
4. `~/.claude/skills/`

## 下一步

- [存储 SPI](./storage-spi) - 扩展存储后端
