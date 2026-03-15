---
title: 认证 API
sidebar_position: 3
description: 需要认证的 API
---

# 认证 API

## 认证相关

### 获取当前用户

```http
GET /api/v1/auth/me
```

### 登出

```http
POST /api/v1/auth/logout
```

## 技能发布

```http
POST /api/v1/publish
Content-Type: multipart/form-data

file: <zip-file>
namespace: <namespace-slug>
```

## 收藏

```http
POST /api/v1/skills/{namespace}/{slug}/star
DELETE /api/v1/skills/{namespace}/{slug}/star
```

## 评分

```http
POST /api/v1/skills/{namespace}/{slug}/rating
Content-Type: application/json

{
  "score": 5
}
```

## 标签管理

```http
GET /api/v1/skills/{namespace}/{slug}/tags
PUT /api/v1/skills/{namespace}/{slug}/tags/{tagName}
DELETE /api/v1/skills/{namespace}/{slug}/tags/{tagName}
```

## 我的资源

```http
GET /api/v1/me/stars
GET /api/v1/me/skills
```

## 命名空间管理

```http
POST /api/v1/namespaces
PUT /api/v1/namespaces/{slug}
GET /api/v1/namespaces/{slug}/members
POST /api/v1/namespaces/{slug}/members
PUT /api/v1/namespaces/{slug}/members/{userId}/role
DELETE /api/v1/namespaces/{slug}/members/{userId}
```

## 审核

```http
GET /api/v1/namespaces/{slug}/reviews
POST /api/v1/namespaces/{slug}/reviews/{id}/approve
POST /api/v1/namespaces/{slug}/reviews/{id}/reject
```

## 提升申请

```http
POST /api/v1/namespaces/{slug}/skills/{skillId}/promote
```

## API Token

```http
POST /api/v1/tokens
GET /api/v1/tokens
DELETE /api/v1/tokens/{id}
```

## 下一步

- [CLI 兼容层](./cli-compat) - ClawHub 兼容接口
