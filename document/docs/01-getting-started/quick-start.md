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
docker compose -f compose.dev.yml up -d --build
```

## 默认账号

两种启动方式都会默认创建一个 bootstrap 管理员账号：

- 用户名：`admin`
- 密码：`ChangeMe!2026`

### `curl` 一键部署

| 服务 | 地址 |
|------|------|
| Web UI | http://localhost |
| Backend API | http://localhost:8080 |

使用上述默认账号密码登录即可。**生产环境请务必修改密码。**

### `compose.dev.yml` 本地开发

| 服务 | 地址 |
|------|------|
| Web UI | http://localhost:3000 |
| Backend API | http://localhost:8080 |
| MinIO Console | http://localhost:9001 |

除了上述 bootstrap 管理员，本地开发还预置两个模拟用户（无需密码）：

| 用户 | 角色 | 说明 |
|------|------|------|
| `local-user` | 普通用户 | 可发布技能、管理命名空间 |
| `local-admin` | 超级管理员 | 拥有所有权限，包括审核和用户管理 |

使用 `X-Mock-User-Id` 请求头切换模拟用户。
如需关闭 bootstrap 管理员，启动前设置 `BOOTSTRAP_ADMIN_ENABLED=false`。

## 常用命令

```bash
# 启动完整开发环境
docker compose -f compose.dev.yml up -d --build

# 停止所有服务
docker compose -f compose.dev.yml down

# 重置并重新启动（会删除数据卷）
docker compose -f compose.dev.yml down -v
docker compose -f compose.dev.yml up -d --build

# 查看所有可用命令
make help
```

## 下一步

- [产品概述](./overview) - 深入了解产品特性
- [典型应用场景](./use-cases) - 探索企业应用场景
- [单机部署](../administration/deployment/single-machine) - 生产环境部署指南
