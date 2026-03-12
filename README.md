# SkillHub

An enterprise-grade AI skill registry — publish, discover, and
manage reusable skill packages across your organization.

SkillHub is a self-hosted platform that gives teams a private,
governed place to share AI skills. Publish a skill package, push
it to a namespace, and let others find it through search or
install it via CLI. Built for on-premise deployment behind your
firewall, with the same polish you'd expect from a public registry.

## Highlights

- **Publish & Version** — Upload skill packages with semantic
  versioning, custom tags (`beta`, `stable`), and automatic
  `latest` tracking.
- **Discover** — Full-text search with filters by namespace,
  downloads, ratings, and recency. Visibility rules ensure
  users only see what they should.
- **Namespaces** — Organize skills under team or global scopes.
  Each namespace has its own members, roles (Owner / Admin /
  Member), and publishing policies.
- **Review Workflow** — Team admins review within their namespace;
  platform admins gate promotions to the global scope. Every
  action is audit-logged.
- **CLI-First** — Native REST API plus a compatibility layer for
  existing ClawHub CLI tools — no client changes needed.
- **Pluggable Storage** — Local filesystem for development, S3 for
  production. Swap via config.

## Quick Start

### Prerequisites

- Docker & Docker Compose

### One command

```bash
make prod-up
```

Then open http://localhost in your browser.

### Development

```bash
# Start infrastructure (PostgreSQL, Redis, MinIO)
make dev

# Backend (in one terminal)
make dev-server

# Frontend (in another terminal)
make dev-web
```

Run `make help` to see all available commands.
