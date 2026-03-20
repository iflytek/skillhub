---
title: 快速开始
sidebar_position: 2
description: 一键启动 SkillHub 开发环境
---

# 快速开始

## 一键启动

使用以下命令一键启动完整的 SkillHub 环境：

```bash
curl -fsSL https://raw.githubusercontent.com/iflytek/skillhub/main/scripts/runtime.sh | sh -s -- up
```

或者克隆仓库后手动启动：

```bash
git clone https://github.com/iflytek/skillhub.git
cd skillhub
make dev-all
```

## 访问地址

启动成功后，可以通过以下地址访问：

| 服务 | 地址 | 说明 |
|------|------|------|
| Web UI | http://localhost:3000 | 前端界面 |
| Backend API | http://localhost:8080 | 后端 API |
| MinIO Console | http://localhost:9001 | 对象存储管理 |

## 开发用户

默认本地开发环境预置了两个模拟测试用户：

| 用户 | 角色 | 说明 |
|------|------|------|
| `local-user` | 普通用户 | 可发布技能、管理命名空间 |
| `local-admin` | 超级管理员 | 拥有所有权限，包括审核和用户管理 |

使用 `X-Mock-User-Id` 请求头在本地开发中模拟用户登录。
本地开发环境还会默认创建可密码登录的 bootstrap 管理员账号：

- 用户名：`admin`
- 密码：`ChangeMe!2026`

本地源码启动如需覆盖或关闭，请在启动后端前设置 `BOOTSTRAP_ADMIN_*`
环境变量。
容器或发布环境则通过 `.env.release` 或 Compose 环境变量配置。

## 常用命令

```bash
# 启动完整开发环境
make dev-all

# 停止所有服务
make dev-all-down

# 重置并重新启动
make dev-all-reset

# 仅启动后端
make dev

# 仅启动前端
make dev-web

# 查看所有可用命令
make help
```

## 下一步

- [产品概述](./overview) - 深入了解产品特性
- [典型应用场景](./use-cases) - 探索企业应用场景
- [单机部署](../administration/deployment/single-machine) - 生产环境部署指南
