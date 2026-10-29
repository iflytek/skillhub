# 项目简介

SkillHub 是一个专为企业打造的自托管 Agent Skill 注册中心。

在 AI Agent 时代，每个团队都在积累自己的技能包（Skills）。但这些技能包散落在各处：有的在开发者本地，有的在 Git 仓库，有的在内部文档里。团队成员很难发现彼此的工作，更难复用已有的能力。

SkillHub 解决了这个问题。它提供了一个**私有、可控、易用**的技能包注册中心，让团队可以像使用 npm、PyPI 一样管理 Agent Skills。

![项目架构图](/diagrams/architecture.png)

## 核心价值

- **统一分发**：上传技能包并跟踪扫描、审核和发布状态
- **企业级权限**：基于命名空间的 RBAC，支持团队协作和审核流程
- **完整生命周期**：版本管理、标签系统、审核工作流、归档机制
- **部署选择**：Docker Compose 和 Kubernetes，按环境配置依赖和认证
- **安全扫描**：内置 Skill Scanner，自动检测安全风险
- **数据主权**：完全自托管，所有数据都在你的防火墙内

## 技术栈

![前端、后端、数据与存储技术组成](/diagrams/tech-stack-sketch.png)

存储可选择本地文件或 S3 兼容后端；按部署需求显式配置。

| 层级 | 技术 | 说明 |
|------|------|------|
| **前端** | React + Vite + TanStack Router | 现代化 SPA，支持多语言切换 |
| **后端** | Java 21 + Spring Boot | 企业级 REST API |
| **数据库** | PostgreSQL | 全文搜索、Flyway 自动迁移 |
| **缓存** | Redis | 会话管理、热点缓存 |
| **存储** | 本地文件 / S3 兼容存储 | 技能包文件存储，支持本地和云端 |
| **部署** | Docker Compose / K8s | 按部署配置自托管 |

## 核心功能一览

| 功能 | 说明 |
|------|------|
| [Skill 发布与版本管理](/guide/skill-publish) | 一键发布技能包，语义化版本管理 |
| [Skill 搜索与发现](/guide/skill-discovery) | 全文搜索、智能过滤、权限感知 |
| [命名空间与团队管理](/guide/namespace) | 基于命名空间的 RBAC 权限体系 |
| [审核与治理](/guide/review) | 技能与套装审核流程、举报系统 |
| [安全扫描](/guide/scanner) | 内置 Skill Scanner，多引擎安全分析 |
| [用户交互与社交](/guide/social) | 星标、评分、通知系统 |

## 先理解三个对象

| 对象 | 用途 | 下一步 |
| --- | --- | --- |
| Skill | 带有 `SKILL.md` 的单个技能包，安装后由 Agent 发现和使用 | [快速开始](./quickstart.md) |
| Skill Suite | 固定成员版本的技能集合，指定一个成员作为入口技能 | [套装安装与使用](./guide/skill-suites.md) |
| Suite Bundle | 发布者批量上传套装定义和成员包的格式 | [批量导入](./guide/suite-bundle.md) |

注册中心负责分发和治理；技能的实际执行发生在使用者的 Agent 环境中。安装套装不会自动运行全部成员。

## 按角色阅读

- 使用者：[CLI](./guide/cli.md)、[搜索与发现](./guide/skill-discovery.md)、[FAQ](./faq.md)。
- 创作者：[发布与版本管理](./guide/skill-publish.md)、[套装](./guide/skill-suites.md)、[审核](./guide/review.md)。
- 管理员：[命名空间](./guide/namespace.md)、[部署与升级](./guide/deployment.md)、[Kubernetes](./guide/kubernetes.md)。
- 集成开发者：[Runtime 契约](./guide/runtime-integration.md)、[仓库文档索引](https://github.com/iflytek/skillhub/blob/main/docs/README.md)。
