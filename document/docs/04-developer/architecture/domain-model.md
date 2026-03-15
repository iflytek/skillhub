---
title: 领域模型
sidebar_position: 2
description: 核心领域实体和关系
---

# 领域模型

## 核心实体

### Namespace（命名空间）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| slug | varchar(64) | URL 友好标识 |
| display_name | varchar(128) | 展示名 |
| type | enum | `GLOBAL` / `TEAM` |
| description | text | 描述 |
| status | enum | `ACTIVE` / `FROZEN` / `ARCHIVED` |

### NamespaceMember（命名空间成员）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| namespace_id | bigint | 命名空间 ID |
| user_id | varchar(128) | 用户 ID |
| role | enum | `OWNER` / `ADMIN` / `MEMBER` |

### Skill（技能）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| namespace_id | bigint | 所属命名空间 |
| slug | varchar(128) | URL 友好标识 |
| display_name | varchar(256) | 展示名 |
| summary | varchar(512) | 摘要 |
| owner_id | varchar(128) | 主要维护人 |
| visibility | enum | `PUBLIC` / `NAMESPACE_ONLY` / `PRIVATE` |
| status | enum | `ACTIVE` / `HIDDEN` / `ARCHIVED` |
| latest_version_id | bigint | 最新已发布版本 |

**唯一约束**：`(namespace_id, slug)`

### SkillVersion（技能版本）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| skill_id | bigint | 技能 ID |
| version | varchar(32) | semver 版本号 |
| status | enum | `DRAFT` / `PENDING_REVIEW` / `PUBLISHED` / `REJECTED` / `YANKED` |
| manifest_json | json | 文件清单 |
| parsed_metadata_json | json | SKILL.md 解析结果 |

**唯一约束**：`(skill_id, version)`

### SkillTag（技能标签）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| skill_id | bigint | 技能 ID |
| tag_name | varchar(64) | 标签名 |
| target_version_id | bigint | 目标版本 |

**唯一约束**：`(skill_id, tag_name)`

## 坐标系统

技能完整寻址：`@{namespace_slug}/{skill_slug}`

## 下一步

- [安全架构](./security) - 安全设计
