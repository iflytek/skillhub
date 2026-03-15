---
title: API Overview
sidebar_position: 1
description: SkillHub API overview
---

# API Overview

SkillHub provides RESTful APIs for integration and automation.

## API Categories

### Public APIs
- Skill search
- Skill details
- Version list
- Download skills
- No authentication required (for PUBLIC skills)

### Authenticated APIs
- Publish skills
- Favorites/ratings
- Namespace management
- Requires login or Bearer Token

### CLI Compatibility Layer
- ClawHub CLI protocol compatible
- Existing tools can migrate seamlessly

## Response Format

### Unified Response Structure

```json
{
  "code": 0,
  "msg": "Success",
  "data": {},
  "timestamp": "2026-03-15T06:00:00Z",
  "requestId": "req-123"
}
```

### Pagination Response

```json
{
  "code": 0,
  "msg": "Success",
  "data": {
    "items": [],
    "total": 100,
    "page": 1,
    "size": 20
  },
  "timestamp": "2026-03-15T06:00:00Z",
  "requestId": "req-123"
}
```

## Authentication Methods

### Session Cookie
Web side uses Session Cookie authentication.

### Bearer Token
CLI and API integration use Bearer Token:

```bash
Authorization: Bearer <token>
```

### API Token
Can create long-lived API Tokens for automation.

## Idempotency

All write operations support `X-Request-Id` header for idempotency:

```bash
X-Request-Id: <uuid-v4>
```

## Next Steps

- [Public APIs](./public) - View public endpoints
