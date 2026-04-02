# 项目简介

SkillHub 是一个专为企业打造的自托管 Agent Skill 注册中心。

在 AI Agent 时代，每个团队都在积累自己的技能包（Skills）。但这些技能包散落在各处：有的在开发者本地，有的在 Git 仓库，有的在内部文档里。团队成员很难发现彼此的工作，更难复用已有的能力。

SkillHub 解决了这个问题。它提供了一个**私有、可控、易用**的技能包注册中心，让团队可以像使用 npm、PyPI 一样管理 Agent Skills。

![项目架构图](/diagrams/architecture.png)

## 核心价值

- **3 分钟发布**：从本地开发到全球分发，只需一条命令
- **企业级权限**：基于命名空间的 RBAC，支持团队协作和审核流程
- **完整生命周期**：版本管理、标签系统、审核工作流、归档机制
- **开箱即用**：一条 curl 命令启动完整环境
- **安全扫描**：内置 Skill Scanner，自动检测安全风险
- **数据主权**：完全自托管，所有数据都在你的防火墙内

## 技术栈

![技术栈图](/diagrams/tech-stack.png)

| 层级 | 技术 | 说明 |
|------|------|------|
| **前端** | React 19 + Vite + TanStack Router | 现代化 SPA，支持中英文切换 |
| **后端** | Java 21 + Spring Boot 3.2 | 企业级 REST API |
| **数据库** | PostgreSQL 16 | 全文搜索、Flyway 自动迁移 |
| **缓存** | Redis 7 | 会话管理、热点缓存 |
| **存储** | MinIO / S3 | 技能包文件存储，支持本地和云端 |
| **部署** | Docker Compose / K8s | 一键启动，支持自托管 |

## 核心功能一览

| 功能 | 说明 |
|------|------|
| [Skill 发布与版本管理](/guide/skill-publish) | 一键发布技能包，语义化版本管理 |
| [Skill 搜索与发现](/guide/skill-discovery) | 全文搜索、智能过滤、权限感知 |
| [命名空间与团队管理](/guide/namespace) | 基于命名空间的 RBAC 权限体系 |
| [审核与治理](/guide/review) | 多级审核工作流、举报系统 |
| [安全扫描](/guide/scanner) | 内置 Skill Scanner，多引擎安全分析 |
| [用户交互与社交](/guide/social) | 星标、评分、通知系统 |
