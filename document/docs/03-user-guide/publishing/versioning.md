---
title: 版本管理
sidebar_position: 3
description: 技能版本和标签管理
---

# 版本管理

## 语义化版本

SkillHub 使用语义化版本（Semantic Versioning）：`MAJOR.MINOR.PATCH`

- `MAJOR`：不兼容的 API 变更
- `MINOR`：向后兼容的功能新增
- `PATCH`：向后兼容的问题修复

示例：`1.0.0`, `1.1.0`, `2.0.0`

## latest 标签

`latest` 是系统保留标签，自动跟随最新已发布版本，不可手动移动。

## 自定义标签

可创建自定义标签用于版本通道管理：

- `beta` - 测试版本
- `stable` - 稳定版本
- `stable-2026q1` - 季度稳定版本

### 创建/移动标签

```bash
skillhub tag set @team/my-skill beta 1.2.0
```

### 删除标签

```bash
skillhub tag delete @team/my-skill beta
```

## 版本撤回

已发布版本发现问题可撤回：

1. 进入技能详情页
2. 找到目标版本
3. 点击"撤回版本"
4. 确认撤回

撤回后的版本仍可查看，但会标记为不推荐使用。

## 下一步

- [搜索技能](../discovery/search) - 发现技能
