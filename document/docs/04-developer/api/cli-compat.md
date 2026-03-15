---
title: CLI 兼容层
sidebar_position: 4
description: ClawHub CLI 协议兼容层
---

# CLI 兼容层

SkillHub 提供 ClawHub CLI 协议兼容层，现有工具可无缝迁移。

## Well-known 发现

```http
GET /.well-known/clawhub.json
```

响应：

```json
{
  "apiBase": "/api/compat/v1"
}
```

## 兼容层 API

### Whoami

```http
GET /api/compat/v1/whoami
```

响应：

```json
{
  "handle": "username",
  "displayName": "User Name",
  "role": "user"
}
```

### 搜索

```http
GET /api/compat/v1/search?q={keyword}&page={page}&limit={limit}
```

响应：

```json
{
  "results": [
    {
      "slug": "my-skill",
      "name": "My Skill",
      "description": "...",
      "author": {
        "handle": "username",
        "displayName": "User Name"
      },
      "version": "1.2.0",
      "downloadCount": 100,
      "starCount": 50,
      "createdAt": "2026-01-01T00:00:00Z",
      "updatedAt": "2026-03-01T00:00:00Z"
    }
  ],
  "total": 1,
  "page": 1,
  "limit": 20
}
```

### 解析

```http
GET /api/compat/v1/resolve?slug={slug}&version={version}
```

响应：

```json
{
  "slug": "my-skill",
  "version": "1.2.0",
  "downloadUrl": "/api/compat/v1/download/my-skill/1.2.0"
}
```

### 下载

```http
GET /api/compat/v1/download/{slug}/{version}
```

### 发布

```http
POST /api/compat/v1/publish
Content-Type: multipart/form-data

file: <zip-file>
```

响应：

```json
{
  "slug": "my-skill",
  "version": "1.0.0",
  "status": "published"
}
```

## 坐标映射

| SkillHub 坐标 | ClawHub canonical slug |
|---------------|------------------------|
| `@global/my-skill` | `my-skill` |
| `@team-name/my-skill` | `team-name--my-skill` |

## 下一步

- [系统架构](../architecture/overview) - 了解架构设计
