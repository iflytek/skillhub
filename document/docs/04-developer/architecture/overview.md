---
title: 系统架构
sidebar_position: 1
description: SkillHub 系统架构概览
---

# 系统架构

## 架构原则

- **单体优先**：一期采用模块化单体，不拆微服务
- **依赖倒置**：领域层不依赖基础设施
- **可替换边界**：搜索、存储都有 SPI 抽象

## 模块结构

```
server/
├── skillhub-app/          # 启动、配置装配、Controller
├── skillhub-domain/       # 领域模型 + 领域服务 + 应用服务
├── skillhub-auth/         # OAuth2 认证 + RBAC + 授权判定
├── skillhub-search/       # 搜索 SPI + PostgreSQL 全文实现
├── skillhub-storage/      # 对象存储抽象 + LocalFile/S3
└── skillhub-infra/        # JPA、通用工具、配置基础
```

## 模块依赖

```
app → domain, auth, search, storage, infra
infra → domain
auth → domain
search → domain
storage → (独立)
```

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 运行时 | Java | 21 |
| 框架 | Spring Boot | 3.2.3 |
| 数据库 | PostgreSQL | 16.x |
| 缓存/会话 | Redis | 7.x |

## 部署架构

```
┌──────────────┐
│ Browser / CLI│
└──────┬───────┘
       │
       ▼
┌──────────────┐
│  Web/Nginx   │
└──────┬───────┘
       │ /api/*
       ▼
┌──────────────┐
│ Spring Boot  │
└───┬────┬─────┘
    │    │
    ▼    ▼
PostgreSQL  Redis
```

## 下一步

- [领域模型](./domain-model) - 核心实体
