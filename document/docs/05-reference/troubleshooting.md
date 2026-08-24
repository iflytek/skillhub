---
title: 故障排查
sidebar_position: 2
description: 常见问题诊断和解决方案
---

# 故障排查

## 服务无法启动

### 检查清单

1. 检查容器状态：`docker compose ps`
2. 查看服务日志：`docker compose logs <service>`
3. 验证环境变量：检查 `.env.release` 配置
4. 检查端口占用：`netstat -tlnp`

### 常见原因

- 端口被占用
- 数据库连接失败
- Redis 连接失败
- 环境变量缺失

### PostgreSQL 容器启动报 `operation not permitted`（写 `postmaster.pid` / `pg_wal` 失败）

SkillHub 默认的 Compose / `runtime.sh` 使用 Docker named volume（`postgres_data`），通常不需要手工处理宿主机目录权限。这个错误更多出现在你把 PostgreSQL 数据目录改成宿主机 bind mount 时，例如 `/data/skillhub/postgres:/var/lib/postgresql/data`。

排查顺序：

1. 优先恢复为 Docker named volume，或直接使用官方 `runtime.sh` 部署脚本，避免手写 compose 时漏配权限。
2. 如果必须使用 bind mount，先确认当前镜像中的 `postgres` 用户 UID/GID：`docker run --rm postgres:16-alpine id postgres`，再按实际 UID/GID 调整数据目录属主，例如 `chown -R <uid>:<gid> <数据目录>`。不要固定假设所有环境都是 `999:999`。
3. 在 RHEL/CentOS 上检查 SELinux；在启用 AppArmor、rootless Docker、NFS/CIFS/NAS 等环境时，也要确认宿主文件系统是否允许 PostgreSQL 需要的写入、锁和权限变更。
4. 不建议把 PostgreSQL `PGDATA` 放在不支持完整 POSIX 权限语义的网络文件系统上；生产环境优先使用本地盘、Docker named volume、块存储或外部 PostgreSQL。

## 上传失败

### 技能包上传失败

1. 检查文件大小
2. 检查文件类型
3. 检查 SKILL.md 格式
4. 查看服务端日志

## 认证问题

### 无法登录

1. 检查 OAuth 配置
2. 检查回调地址配置
3. 检查 `SKILLHUB_PUBLIC_BASE_URL` 配置

## 性能问题

### 搜索慢

1. 检查 PostgreSQL 全文索引
2. 考虑升级到 Elasticsearch（后续版本）

### 下载慢

1. 检查对象存储配置
2. 检查网络带宽

## 获取帮助

如以上方案无法解决问题：
1. 查看日志
2. 提交 Issue
3. 联系技术支持

## 下一步

- [变更日志](./changelog) - 版本历史
