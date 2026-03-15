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
