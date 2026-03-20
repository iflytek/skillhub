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
make dev-all
```

## Access Points

After successful startup, you can access through the following addresses:

| Service | Address | Description |
|---------|---------|-------------|
| Web UI | http://localhost:3000 | Frontend interface |
| Backend API | http://localhost:8080 | Backend API |
| MinIO Console | http://localhost:9001 | Object storage management |

## Development Users

By default, the local development environment comes with two mock test users:

| User | Role | Description |
|------|------|-------------|
| `local-user` | Regular user | Can publish skills, manage namespaces |
| `local-admin` | Super admin | Has all permissions including review and user management |

Use the `X-Mock-User-Id` request header to simulate user login in local development.
The local development environment also creates a password-based bootstrap
admin by default:

- username: `admin`
- password: `ChangeMe!2026`

For local source startup, override or disable it with `BOOTSTRAP_ADMIN_*`
environment variables before starting the backend.
For container or release environments, configure the same values in
`.env.release` or the Compose environment.

## Common Commands

```bash
# Start complete development environment
make dev-all

# Stop all services
make dev-all-down

# Reset and restart
make dev-all-reset

# Start backend only
make dev

# Start frontend only
make dev-web

# View all available commands
make help
```

## Next Steps

- [Overview](./overview) - Deep dive into product features
- [Use Cases](./use-cases) - Explore enterprise application scenarios
- [Single Machine Deployment](../administration/deployment/single-machine) - Production deployment guide
