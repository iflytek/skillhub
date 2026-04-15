---
title: Quick Start
sidebar_position: 2
description: One-click startup of SkillHub development environment
---

# Quick Start

## One-click Startup

Use the following command to start a complete SkillHub environment with one command:

```bash
curl -fsSL https://raw.githubusercontent.com/iflytek/skillhub/main/scripts/runtime.sh | sh -s -- up
```

Or clone the repository and start manually:

```bash
git clone https://github.com/iflytek/skillhub.git
cd skillhub
docker compose -f compose.dev.yml up -d --build
```

## Default Account

Both startup methods create a bootstrap admin account by default:

- username: `admin`
- password: `ChangeMe!2026`

### `curl` One-click Deployment

| Service | Address |
|---------|---------|
| Web UI | http://localhost |
| Backend API | http://localhost:8080 |

Log in with the default credentials above. **Change the password for production.**

### `compose.dev.yml` Local Development

| Service | Address |
|---------|---------|
| Web UI | http://localhost:3000 |
| Backend API | http://localhost:8080 |
| MinIO Console | http://localhost:9001 |

In addition to the bootstrap admin, local development includes two mock users (no password needed):

| User | Role | Description |
|------|------|-------------|
| `local-user` | Regular user | Can publish skills, manage namespaces |
| `local-admin` | Super admin | Has all permissions including review and user management |

Use the `X-Mock-User-Id` request header to switch mock users.
To disable the bootstrap admin, set `BOOTSTRAP_ADMIN_ENABLED=false` before starting.

## Common Commands

```bash
# Start complete development environment
docker compose -f compose.dev.yml up -d --build

# Stop all services
docker compose -f compose.dev.yml down

# Reset and restart (removes volumes)
docker compose -f compose.dev.yml down -v
docker compose -f compose.dev.yml up -d --build

# View all available commands
make help
```

## Next Steps

- [Overview](./overview) - Deep dive into product features
- [Use Cases](./use-cases) - Explore enterprise application scenarios
- [Single Machine Deployment](../administration/deployment/single-machine) - Production deployment guide
