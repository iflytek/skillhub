---
title: 配置说明
sidebar_position: 3
description: SkillHub 配置项详细说明
---

# 配置说明

## 环境变量

SkillHub 通过环境变量进行配置，主要配置项如下：

### 基础配置

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `SKILLHUB_PUBLIC_BASE_URL` | 公网访问地址 | - |
| `SKILLHUB_VERSION` | 镜像版本 | `edge` |

### 数据库配置

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `POSTGRES_HOST` | PostgreSQL 主机 | `postgres` |
| `POSTGRES_PORT` | PostgreSQL 端口 | `5432` |
| `POSTGRES_DB` | 数据库名 | `skillhub` |
| `POSTGRES_USER` | 数据库用户 | `skillhub` |
| `POSTGRES_PASSWORD` | 数据库密码 | - |

### Redis 配置

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `REDIS_HOST` | Redis 主机 | `redis` |
| `REDIS_PORT` | Redis 端口 | `6379` |
| `REDIS_PASSWORD` | Redis 密码 | - |

### 存储配置

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `SKILLHUB_STORAGE_PROVIDER` | 存储提供方 | `local` |
| `SKILLHUB_STORAGE_S3_ENDPOINT` | S3 端点 | - |
| `SKILLHUB_STORAGE_S3_BUCKET` | S3 桶名 | - |
| `SKILLHUB_STORAGE_S3_ACCESS_KEY` | S3 Access Key | - |
| `SKILLHUB_STORAGE_S3_SECRET_KEY` | S3 Secret Key | - |
| `SKILLHUB_STORAGE_S3_MAX_CONNECTIONS` | S3 HTTP 连接池大小 | `100` |
| `SKILLHUB_STORAGE_S3_CONNECTION_ACQUISITION_TIMEOUT` | 等待连接池连接超时，ISO-8601 Duration | `PT2S` |
| `SKILLHUB_STORAGE_S3_API_CALL_ATTEMPT_TIMEOUT` | 单次 S3 请求尝试超时，ISO-8601 Duration | `PT10S` |
| `SKILLHUB_STORAGE_S3_API_CALL_TIMEOUT` | 整个 S3 请求总超时，ISO-8601 Duration | `PT30S` |

### OAuth 配置

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `OAUTH2_GITHUB_CLIENT_ID` | GitHub OAuth Client ID | - |
| `OAUTH2_GITHUB_CLIENT_SECRET` | GitHub OAuth Client Secret | - |

### 首登管理员配置

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `BOOTSTRAP_ADMIN_ENABLED` | 是否启用首登管理员 | `false` |
| `BOOTSTRAP_ADMIN_USERNAME` | 首登管理员用户名 | - |
| `BOOTSTRAP_ADMIN_PASSWORD` | 首登管理员密码 | - |

## 配置文件

Spring Boot 配置文件位于 `server/skillhub-app/src/main/resources/`。

## 下载与对象存储建议

- 生产环境建议显式配置 S3 连接池和超时参数，避免对象存储抖动时大量线程阻塞在连接池等待。
- 版本列表接口现在使用数据库中的下载状态元数据，不再实时探测对象存储；部署时请确保数据库迁移已经执行。
- 下载接口按用户/IP + namespace + slug + version/tag 进行更细粒度限流。如有批量分发场景，建议在入口层或专用账号策略中单独放宽，不建议直接关闭应用层限流。

## 下一步

- [认证配置](../security/authentication) - 配置身份认证
- [下载与对象存储排查](../../05-reference/download-troubleshooting) - 排查下载、限流和对象存储故障
