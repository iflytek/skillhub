---
title: 系统架构
sidebar_position: 1
description: SkillHub 系统架构概览
---

# 系统架构

## 架构原则

SkillHub 采用了清晰的分层架构，从上到下分为表现层、应用层、领域层和基础设施层。
表现层由 REST API 和 React 前端组成，负责与用户的直接交互。应用层处理业务用例的编排和 DTO 转换。领域层是核心业务逻辑的所在，包含丰富的领域模型和业务规则。基础设施层则处理数据库、存储和搜索等底层能力。

在设计上：
- **单体优先**：一期采用模块化单体，不拆微服务
- **领域驱动设计（DDD）**：引入 DDD、CQRS 读写分离模式和事件溯源机制，保证了系统在复杂业务场景下的可维护性和可扩展性
- **依赖倒置**：领域层不依赖基础设施
- **可替换边界**：搜索、存储都有 SPI 抽象

## 模块结构

SkillHub 采用了多模块 Maven 项目结构，各个模块职责明确：

```
server/
├── skillhub-app/          # 主应用入口：启动、配置装配、Controller
├── skillhub-domain/       # 核心业务逻辑：领域模型 + 领域服务 + 应用服务
├── skillhub-auth/         # 认证授权：OAuth2 认证 + RBAC + 授权判定
├── skillhub-search/       # 搜索功能：搜索 SPI + PostgreSQL 全文实现
├── skillhub-storage/      # 存储层抽象：对象存储抽象 + LocalFile/S3/MinIO
└── skillhub-infra/        # 基础设施：JPA、通用工具、配置基础
```

## 模块依赖

模块之间的边界非常清晰：

```
app → domain, auth, search, storage, infra
infra → domain
auth → domain
search → domain
storage → (独立)
```

## 技术栈


| 层级 | 技术 | 版本 / 说明 |
|------|------|-------------|
| 前端 | React 19 + Vite + TanStack Router | 现代化 SPA，支持中英文切换 |
| 后端运行时 | Java | 21 |
| 框架 | Spring Boot | 3.2.3 (企业级 REST API) |
| 数据库 | PostgreSQL | 16.x (全文搜索、Flyway 自动迁移) |
| 缓存/会话 | Redis | 7.x (会话管理、热点数据缓存) |
| 存储 | MinIO / S3 / 本地存储 | 技能包文件存储，支持本地和云端灵活切换 |
| 监控 | Prometheus + Grafana | 内置监控方案，开箱即用 |

## 部署架构

SkillHub 提供了多种部署方式。最简单的即通过 Docker Compose 一键部署，可拉起 Web UI、Backend API、PostgreSQL、Redis、MinIO 和 Skill Scanner 等完整环境。

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
    │
    ▼
  MinIO
```

## 下一步

- [领域模型](./domain-model) - 核心实体
