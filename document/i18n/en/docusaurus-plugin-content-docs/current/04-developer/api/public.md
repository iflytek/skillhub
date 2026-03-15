---
title: Public APIs
sidebar_position: 2
description: Public APIs without authentication
---

# Public APIs

## Skill Search

```http
GET /api/v1/skills?keyword=...&namespace=...&page=1&size=20
```

**Query Parameters:**
- `keyword`: Search keyword
- `namespace`: Namespace filter
- `page`: Page number
- `size`: Page size

## Skill Details

```http
GET /api/v1/skills/{namespace}/{slug}
```

## Version List

```http
GET /api/v1/skills/{namespace}/{slug}/versions
```

## Version Details

```http
GET /api/v1/skills/{namespace}/{slug}/versions/{version}
```

## File List

```http
GET /api/v1/skills/{namespace}/{slug}/versions/{version}/files
```

## Download Skill

```http
GET /api/v1/skills/{namespace}/{slug}/download
GET /api/v1/skills/{namespace}/{slug}/versions/{version}/download
```

## Resolve Version

```http
GET /api/v1/skills/{namespace}/{slug}/resolve?version=...&tag=...
```

## Namespace List

```http
GET /api/v1/namespaces
```

## Namespace Details

```http
GET /api/v1/namespaces/{slug}
```

## Next Steps

- [Authenticated APIs](./authenticated) - View authenticated endpoints
