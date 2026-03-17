---
title: 用户管理
sidebar_position: 3
description: 平台用户管理
---

# 用户管理

## 用户状态

| 状态 | 实际逻辑 |
|------|----------|
| `ACTIVE` | 可正常登录和使用系统。OAuth 首次自动准入、local 注册成功后都会进入该状态。 |
| `PENDING` | 账号已建但不可登录。OAuth 在“需要审批”策略下会创建 `PENDING` 用户并跳转到待审批页；local 登录遇到该状态会直接拒绝。 |
| `DISABLED` | 不可登录。OAuth 和 local 登录都会拒绝；`/api/v1/auth/me` 发现当前会话对应用户已被禁用时，会直接清掉 session。 |
| `MERGED` | 账号已并入其他账号，不可继续登录；主要由账号合并流程写入，不是普通用户管理流程的目标状态。 |

## 用户准入

可配置新用户是否需要审批：
- 自动准入：新用户登录后自动激活
- 审批准入：新用户需 USER_ADMIN 审批后激活

## 角色分配

`USER_ADMIN` 或 `SUPER_ADMIN` 可调用用户管理接口修改平台角色，但当前实现有几个关键点：

- 接口一次只能设置一个目标平台角色。
- 设置时会删除该用户已有的显式平台角色，再写入新的那个角色。
- 如果设置为 `USER`，不会写入 `user_role_binding`，而是依赖运行时默认角色补位。
- `USER_ADMIN` 不能分配 `SUPER_ADMIN`，只有 `SUPER_ADMIN` 能分配。

当前管理接口可设置的目标角色实际上是：

- `USER`
- `SKILL_ADMIN`
- `USER_ADMIN`
- `AUDITOR`
- `SUPER_ADMIN`

## 用户封禁/解封

`USER_ADMIN` 或 `SUPER_ADMIN` 可封禁/解封用户。

当前公开管理接口只支持把状态改成：

- `ACTIVE`
- `DISABLED`

其中：

- “审批通过”本质上也是把用户状态改成 `ACTIVE`。
- 不能通过该接口直接改成 `PENDING` 或 `MERGED`。

## 账号合并

支持将多个账号合并为一个，保留操作历史。

## 下一步

- [创建技能包](../../user-guide/publishing/create-skill) - 开始发布技能
