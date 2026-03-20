---
title: Single Machine Deployment
sidebar_position: 1
description: Deploy SkillHub using Docker Compose on a single machine
---

# Single Machine Deployment

This guide describes how to deploy SkillHub on a single server using Docker Compose.

## Prerequisites

- Docker Engine 20.10+
- Docker Compose Plugin 2.0+
- At least 4GB available RAM
- At least 20GB available disk space

## Quick Deployment

```bash
# 1. Clone the repository
git clone https://github.com/iflytek/skillhub.git
cd skillhub

# 2. Copy environment variable template
cp .env.release.example .env.release

# 3. Edit configuration
# Modify configuration items in .env.release, especially passwords and public URLs

# 4. Validate configuration
make validate-release-config

# 5. Start services
docker compose --env-file .env.release -f compose.release.yml up -d
```

## Configuration

See [Configuration](./configuration) documentation for details.

## Verify Deployment

```bash
# Check container status
docker compose --env-file .env.release -f compose.release.yml ps

# Check backend health
curl -i http://127.0.0.1:8080/actuator/health

# Access Web UI
# Open http://localhost in browser (or configured public URL)
```

## First Login Configuration

1. Login with `BOOTSTRAP_ADMIN_USERNAME` and `BOOTSTRAP_ADMIN_PASSWORD`
   When copied from `.env.release.example`, the shipped placeholder is `replace-this-admin-password`, so change it before first login
2. Change admin password immediately
3. Configure enterprise SSO (optional)
4. Create team namespaces

## Next Steps

- [Configuration](./configuration) - Detailed configuration reference
- [Kubernetes Deployment](./kubernetes) - High availability deployment
