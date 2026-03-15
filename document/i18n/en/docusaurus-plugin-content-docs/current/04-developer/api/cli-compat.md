---
title: CLI Compatibility Layer
sidebar_position: 4
description: ClawHub CLI protocol compatibility layer
---

# CLI Compatibility Layer

SkillHub provides a ClawHub CLI protocol compatibility layer for seamless migration of existing tools.

## Configuring ClawHub CLI

To connect ClawHub CLI to your SkillHub instance, configure the following environment variables:

### Environment Variable Configuration

**Linux/macOS (bash/zsh):**
```bash
# ~/.bashrc or ~/.zshrc
export CLAWHUB_SITE=https://skill.xfyun.cn
export CLAWHUB_REGISTRY=https://skill.xfyun.cn
```

**Windows (PowerShell):**
```powershell
# Permanent setting (current user)
[Environment]::SetEnvironmentVariable('CLAWHUB_SITE', 'https://skill.xfyun.cn', 'User')
[Environment]::SetEnvironmentVariable('CLAWHUB_REGISTRY', 'https://skill.xfyun.cn', 'User')

# Or temporary setting (current session)
$env:CLAWHUB_SITE = 'https://skill.xfyun.cn'
$env:CLAWHUB_REGISTRY = 'https://skill.xfyun.cn'
```

### Using CLI Flags (Single Command)

```bash
clawhub --site https://skill.xfyun.cn --registry https://skill.xfyun.cn install <skill>
```

### One-click Copy from Web UI

The SkillHub skill detail page automatically displays install commands with the correct environment variables pre-configured. Simply copy and use.

## Well-known Discovery

```http
GET /.well-known/clawhub.json
```

Response:

```json
{
  "apiBase": "/api/v1"
}
```

## Compatibility Layer APIs

### Whoami

```http
GET /api/v1/whoami
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
GET /api/v1/search?q={keyword}&page={page}&limit={limit}
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
GET /api/v1/resolve?slug={slug}&version={version}
```

Response:

```json
{
  "slug": "my-skill",
  "version": "1.2.0",
  "downloadUrl": "/api/v1/download/my-skill/1.2.0"
}
```

### Download

```http
GET /api/v1/download/{slug}/{version}
```

### Publish

```http
POST /api/v1/publish
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
