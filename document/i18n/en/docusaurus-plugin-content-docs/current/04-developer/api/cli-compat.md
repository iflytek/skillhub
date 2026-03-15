---
title: CLI Compatibility Layer
sidebar_position: 4
description: ClawHub CLI protocol compatibility layer
---

# CLI Compatibility Layer

SkillHub provides a ClawHub CLI protocol compatibility layer for seamless migration of existing tools.

## Well-known Discovery

```http
GET /.well-known/clawhub.json
```

Response:

```json
{
  "apiBase": "/api/compat/v1"
}
```

## Compatibility Layer APIs

### Whoami

```http
GET /api/compat/v1/whoami
```

Response:

```json
{
  "handle": "username",
  "displayName": "User Name",
  "role": "user"
}
```

### Search

```http
GET /api/compat/v1/search?q={keyword}&page={page}&limit={limit}
```

Response:

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

### Resolve

```http
GET /api/compat/v1/resolve?slug={slug}&version={version}
```

Response:

```json
{
  "slug": "my-skill",
  "version": "1.2.0",
  "downloadUrl": "/api/compat/v1/download/my-skill/1.2.0"
}
```

### Download

```http
GET /api/compat/v1/download/{slug}/{version}
```

### Publish

```http
POST /api/compat/v1/publish
Content-Type: multipart/form-data

file: <zip-file>
```

Response:

```json
{
  "slug": "my-skill",
  "version": "1.0.0",
  "status": "published"
}
```

## Coordinate Mapping

| SkillHub Coordinate | ClawHub canonical slug |
|---------------------|------------------------|
| `@global/my-skill` | `my-skill` |
| `@team-name/my-skill` | `team-name--my-skill` |

## Next Steps

- [System Architecture](../architecture/overview) - Understand architecture design
