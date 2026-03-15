---
title: 产品概述
sidebar_position: 1
description: SkillHub 产品概述和核心特性介绍
---

# 产品概述

SkillHub 是企业级 AI 技能注册平台，支持技能发布、发现与管理，采用自托管架构保障数据安全。

## 核心特性

### 发布管理
- 版本控制与语义化版本（Semantic Versioning）
- 自定义标签（如 `beta`/`stable`）
- `latest` 标签自动跟随最新发布版本

### 发现机制
- 全文搜索
- 多维度筛选（命名空间、下载量、评分）
- 可见性控制（公开/命名空间内/私有）

### 组织架构
- 命名空间隔离
- 基于角色的访问控制（RBAC）
- 团队与全局双层空间

### 治理体系
- 双层审核流程
- 审计日志
- 权限分离

### 存储与部署
- 支持 S3/MinIO/本地存储
- Docker/Kubernetes 部署
- 企业级可观测性

## 技术栈

### 后端
- **Java 21** - 运行时
- **Spring Boot 3.2.3** - 应用框架
- **PostgreSQL 16.x** - 主数据库 + 全文搜索
- **Redis 7.x** - 缓存与会话存储

### 前端
- **React 19** - UI 框架
- **TypeScript** - 类型安全
- **Vite** - 构建工具
- **Tailwind CSS** - 样式框架

### 部署
- **Docker Compose** - 单机部署
- **Kubernetes** - 生产环境编排

## 核心概念

### 命名空间
技能隔离边界，支持 `@global`（全局）和 `@team-*`（团队）前缀。

### 坐标系统
技能标识格式为 `@{namespace_slug}/{skill_slug}`，支持语义化版本。

### 兼容性
提供 REST API 和 ClawHub 兼容层，支持现有工具集成。

## 下一步

- [快速开始](./quick-start) - 一键启动体验
- [典型应用场景](./use-cases) - 了解如何在企业中应用
