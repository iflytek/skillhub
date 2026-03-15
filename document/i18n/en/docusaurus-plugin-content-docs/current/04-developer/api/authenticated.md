---
title: Authenticated APIs
sidebar_position: 3
description: APIs requiring authentication
---

# Authenticated APIs

## Authentication Related

### Get Current User

```http
GET /api/v1/auth/me
```

### Logout

```http
POST /api/v1/auth/logout
```

## Skill Publishing

```http
POST /api/v1/publish
Content-Type: multipart/form-data

file: <zip-file>
namespace: <namespace-slug>
```

## Favorites

```http
POST /api/v1/skills/{namespace}/{slug}/star
DELETE /api/v1/skills/{namespace}/{slug}/star
```

## Ratings

```http
POST /api/v1/skills/{namespace}/{slug}/rating
Content-Type: application/json

{
  "score": 5
}
```

## Tag Management

```http
GET /api/v1/skills/{namespace}/{slug}/tags
PUT /api/v1/skills/{namespace}/{slug}/tags/{tagName}
DELETE /api/v1/skills/{namespace}/{slug}/tags/{tagName}
```

## My Resources

```http
GET /api/v1/me/stars
GET /api/v1/me/skills
```

## Namespace Management

```http
POST /api/v1/namespaces
PUT /api/v1/namespaces/{slug}
GET /api/v1/namespaces/{slug}/members
POST /api/v1/namespaces/{slug}/members
PUT /api/v1/namespaces/{slug}/members/{userId}/role
DELETE /api/v1/namespaces/{slug}/members/{userId}
```

## Reviews

```http
GET /api/v1/namespaces/{slug}/reviews
POST /api/v1/namespaces/{slug}/reviews/{id}/approve
POST /api/v1/namespaces/{slug}/reviews/{id}/reject
```

## Promotion Requests

```http
POST /api/v1/namespaces/{slug}/skills/{skillId}/promote
```

## API Token

```http
POST /api/v1/tokens
GET /api/v1/tokens
DELETE /api/v1/tokens/{id}
```

## Next Steps

- [CLI Compatibility Layer](./cli-compat) - ClawHub compatible endpoints
