---
title: 单机部署
sidebar_position: 1
description: 使用 Docker Compose 单机部署 SkillHub
---

# 单机部署

本文介绍如何使用 Docker Compose 在单台服务器上部署 SkillHub。

## 前置要求

- Docker Engine 20.10+
- Docker Compose Plugin 2.0+
- 至少 4GB 可用内存
- 至少 20GB 可用磁盘空间

## 快速部署

```bash
# 1. 克隆仓库
git clone https://github.com/iflytek/skillhub.git
cd skillhub

# 2. 复制环境变量模板
cp .env.release.example .env.release

# 3. 编辑配置
# 修改 .env.release 中的配置项，特别是密码和公网地址

# 4. 验证配置
make validate-release-config

# 5. 启动服务
docker compose --env-file .env.release -f compose.release.yml up -d
```

## 配置说明

详见 [配置说明](./configuration) 文档。

## 验证部署

```bash
# 检查容器状态
docker compose --env-file .env.release -f compose.release.yml ps

# 检查后端健康状态
curl -i http://127.0.0.1:8080/actuator/health

# 访问 Web UI
# 浏览器打开 http://localhost（或配置的公网地址）
```

## 首登配置

1. 使用 `BOOTSTRAP_ADMIN_USERNAME` 和 `BOOTSTRAP_ADMIN_PASSWORD` 登录
   从 `.env.release.example` 复制配置时，默认占位值是 `replace-this-admin-password`，需要先改掉
2. 立即修改管理员密码
3. 配置企业 SSO（可选）
4. 创建团队命名空间

## 下一步

- [配置说明](./configuration) - 详细配置项说明
- [Kubernetes 部署](./kubernetes) - 高可用部署
