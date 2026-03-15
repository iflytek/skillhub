---
title: 公开 API
sidebar_position: 2
description: 无需认证的公开 API
---

# 公开 API

## 技能搜索

```http
GET /api/v1/skills?keyword=...&namespace=...&page=1&size=20
```

**Query Parameters:**
- `keyword`: 搜索关键词
- `namespace`: 命名空间筛选
- `page`: 页码
- `size`: 每页数量

## 技能详情

```http
GET /api/v1/skills/{namespace}/{slug}
```

## 版本列表

```http
GET /api/v1/skills/{namespace}/{slug}/versions
```

## 版本详情

```http
GET /api/v1/skills/{namespace}/{slug}/versions/{version}
```

## 文件清单

```http
GET /api/v1/skills/{namespace}/{slug}/versions/{version}/files
```

## 下载技能

```http
GET /api/v1/skills/{namespace}/{slug}/download
GET /api/v1/skills/{namespace}/{slug}/versions/{version}/download
```

## 解析版本

```http
GET /api/v1/skills/{namespace}/{slug}/resolve?version=...&tag=...
```

## 命名空间列表

```http
GET /api/v1/namespaces
```

## 命名空间详情

```http
GET /api/v1/namespaces/{slug}
```

## 下一步

- [认证 API](./authenticated) - 查看认证接口
