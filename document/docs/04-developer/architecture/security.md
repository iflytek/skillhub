---
title: 安全架构
sidebar_position: 3
description: 安全架构设计
---

# 安全架构

## 认证架构

### OAuth2 登录

- 基于 Spring Security OAuth2 Client
- 一期支持 GitHub
- 架构支持扩展多 Provider

### CLI 认证

- OAuth Device Flow
- Web 授权后签发 CLI 凭证
- 支持 API Token

### Session 管理

- Spring Session + Redis
- 分布式 Session 共享
- 支持多 Pod 部署

## 授权架构

### 平台角色

| 角色 | 权限 |
|------|------|
| `SUPER_ADMIN` | 所有权限 |
| `SKILL_ADMIN` | 技能治理 |
| `USER_ADMIN` | 用户治理 |
| `AUDITOR` | 审计只读 |

### 命名空间角色

| 角色 | 权限 |
|------|------|
| `OWNER` | 命名空间所有者 |
| `ADMIN` | 审核、成员管理 |
| `MEMBER` | 发布技能 |

### 可见性规则

| 可见性 | 谁可访问 |
|--------|---------|
| `PUBLIC` | 任何人（匿名） |
| `NAMESPACE_ONLY` | 命名空间成员 |
| `PRIVATE` | owner + 命名空间 ADMIN |

## 审计

所有关键操作同步写入审计日志：
- 发布、下载、删除
- 审核通过、拒绝
- 权限变更
- 配置变更

## 限流

- Ingress 层基础限流（Nginx）
- 应用层精细限流（Redis 滑动窗口）

## 下一步

- [技能协议](../plugins/skill-protocol) - 技能包规范
