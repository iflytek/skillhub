# 项目简介

SkillHub 是一个面向企业的自托管 Agent Skill 注册中心，用来统一管理团队内部的技能包发布、发现、治理与复用。

在很多团队里，技能包分散在本地目录、Git 仓库和内部文档中，结果通常是：

- 很难发现别人已经做过什么
- 很难判断哪个版本可用、可信、可复用
- 很难建立统一的权限和治理边界

SkillHub 的目标是把这些问题收敛到一个私有、可控、可审计的平台里。

![项目架构图](/diagrams/architecture.png)

## 核心价值

- 私有部署，数据主权可控
- 命名空间 + RBAC 支撑团队协作
- 发布、版本、审核、扫描形成完整生命周期
- 提供 Web 与 CLI 两种使用入口

## 技术基线

| 层级 | 技术 |
|------|------|
| 前端 | React 19 + Vite |
| 后端 | Java 17 + Spring Boot 3.2 |
| 数据库 | MySQL 8 |
| 缓存 | Redis 7 |
| 存储 | MinIO / S3 |
| 部署 | Docker Compose / Kubernetes |

## 下一步

- [快速开始](/quickstart)
- [Skill 发布与版本管理](/guide/skill-publish)
- [Skill 搜索与发现](/guide/skill-discovery)
- [命名空间与团队管理](/guide/namespace)
