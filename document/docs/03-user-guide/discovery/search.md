---
title: 搜索技能
sidebar_position: 1
description: 搜索和筛选技能
---

# 搜索技能

## 全文搜索

在搜索框输入关键词，SkillHub 会在以下字段中搜索：
- 技能名称
- 技能描述
- 技能 slug
- frontmatter 中除 `name`、`description`、`version` 外的其他字段
- `keywords` / `tags` 等关键词字段

## 筛选条件

可通过以下条件筛选搜索结果：
- 命名空间
- 可见性
- 下载量排序
- 评分排序
- 更新时间排序

## 高级搜索

使用搜索语法：
- `namespace:@team-ai` - 指定命名空间
- `category:code-review` - 指定分类
- `downloads:>100` - 下载量大于 100

## 下一步

- [安装使用](./install) - 安装和使用技能
