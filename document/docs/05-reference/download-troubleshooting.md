---
title: 下载与对象存储排查
sidebar_position: 6
description: 排查下载接口、版本列表下载可用性和对象存储连接池问题
---

# 下载与对象存储排查

本页用于排查下载接口、版本列表中的下载可用性，以及对象存储连接池耗尽等问题。

## 当前行为

- 版本列表接口不再实时访问对象存储，而是读取数据库里的 `skill_version.download_ready`
- 下载接口优先返回预签名 URL；只有需要代理流式下载时才真正打开对象流
- 当 bundle 缺失时，服务会记录告警，并回退到逐文件重新打包

## 重点指标

- `skillhub.skill.download.delivery`
  - 标签：`mode=redirect|stream`
  - 标签：`fallback_bundle=true|false`
- `skillhub.skill.download.bundle_missing_fallback`
  - bundle 缺失并触发逐文件回退打包的次数
- `skillhub.storage.failure`
  - 标签：`operation=exists|getMetadata|getObject|putObject|deleteObject|deleteObjects|generatePresignedUrl`
- `skillhub.ratelimit.exceeded`
  - 标签：`category=download|search|publish|...`

## 常见症状

### 下载返回 503

接口现在会把对象存储访问失败映射为 `503 error.storage.unavailable`，不再统一返回 500。

优先检查：

- `skillhub.storage.failure` 是否持续上升
- 对象存储端点、凭证、桶名是否正确
- `SKILLHUB_STORAGE_S3_MAX_CONNECTIONS`
- `SKILLHUB_STORAGE_S3_CONNECTION_ACQUISITION_TIMEOUT`
- `SKILLHUB_STORAGE_S3_API_CALL_ATTEMPT_TIMEOUT`
- `SKILLHUB_STORAGE_S3_API_CALL_TIMEOUT`

### 日志出现 bundle missing fallback

说明数据库里该版本仍被认为可下载，但对象存储中的 `packages/{skillId}/{versionId}/bundle.zip` 已缺失。

影响：

- 下载仍可通过逐文件打包完成
- 性能会明显差于直接下载 bundle

建议：

- 优先补齐缺失 bundle
- 观察 `skillhub.skill.download.bundle_missing_fallback`
- 检查对象存储生命周期规则、人工清理脚本或历史数据不一致问题

### 用户频繁点击下载后被限流

下载限流键现在包含：

- 用户或 IP
- namespace
- slug
- version 或 tag

这样同一用户反复下载同一版本时，不会把所有下载请求混进一个粗粒度桶里。

如果确实需要更高吞吐：

- 在入口层做白名单
- 为自动化分发账号单独放宽策略
- 不建议直接移除应用层下载限流
