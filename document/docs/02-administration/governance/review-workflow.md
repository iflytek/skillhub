---
title: 审核流程
sidebar_position: 2
description: 技能发布审核流程配置
---

# 审核流程

SkillHub 采用双层审核机制，保障技能质量。

## 审核流程

### 团队空间技能

1. 团队成员提交发布
2. 创建审核任务（PENDING）
3. 团队 ADMIN 或 OWNER 审核
   - 通过 → 技能发布（PUBLISHED）
   - 拒绝 → 返回修改（REJECTED）

### 全局空间技能

1. 提交发布
2. 平台 SKILL_ADMIN 或 SUPER_ADMIN 审核
3. 审核通过后发布

## 团队技能提升到全局

1. 团队技能已发布
2. 团队 ADMIN 或 OWNER 申请"提升到全局"
3. 平台管理员审核
4. 审核通过后在全局空间创建新技能

## 审核权限

| 审核类型 | 所需角色 |
|---------|---------|
| 团队空间技能审核 | 命名空间 ADMIN/OWNER |
| 全局空间技能审核 | SKILL_ADMIN/SUPER_ADMIN |
| 提升申请审核 | SKILL_ADMIN/SUPER_ADMIN |

## 下一步

- [用户管理](./user-management) - 管理平台用户
