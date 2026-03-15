---
title: 用户管理
sidebar_position: 3
description: 平台用户管理
---

# 用户管理

## 用户状态

| 状态 | 说明 |
|------|------|
| `ACTIVE` | 正常使用 |
| `PENDING` | 等待审批 |
| `DISABLED` | 已封禁 |
| `MERGED` | 已合并到其他账号 |

## 用户准入

可配置新用户是否需要审批：
- 自动准入：新用户登录后自动激活
- 审批准入：新用户需 USER_ADMIN 审批后激活

## 角色分配

USER_ADMIN 可分配平台角色：
- SKILL_ADMIN
- USER_ADMIN
- AUDITOR

注意：不可分配 SUPER_ADMIN（仅 SUPER_ADMIN 可分配）。

## 用户封禁/解封

USER_ADMIN 或 SUPER_ADMIN 可封禁/解封用户。

## 账号合并

支持将多个账号合并为一个，保留操作历史。

## 下一步

- [创建技能包](../../03-user-guide/publishing/create-skill) - 开始发布技能
