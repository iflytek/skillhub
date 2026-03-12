# SkillHub

An enterprise-grade agent skill registry — publish, discover, and
manage reusable skill packages across your organization.

SkillHub is a self-hosted platform that gives teams a private,
governed place to share agent skills. Publish a skill package, push
it to a namespace, and let others find it through search or
install it via CLI. Built for on-premise deployment behind your
firewall, with the same polish you'd expect from a public registry.

## Highlights

- **Self-Hosted & Private** — Deploy on your own infrastructure.
  Keep proprietary skills behind your firewall with full data
  sovereignty. One `make dev-all` command to get running locally.
- **Publish & Version** — Upload agent skill packages with semantic
  versioning, custom tags (`beta`, `stable`), and automatic
  `latest` tracking.
- **Discover** — Full-text search with filters by namespace,
  downloads, ratings, and recency. Visibility rules ensure
  users only see what they're authorized to.
- **Team Namespaces** — Organize skills under team or global scopes.
  Each namespace has its own members, roles (Owner / Admin /
  Member), and publishing policies.
- **Review & Governance** — Team admins review within their namespace;
  platform admins gate promotions to the global scope. Every
  action is audit-logged for compliance.
- **CLI-First** — Native REST API plus a compatibility layer for
  existing ClawHub CLI tools — no client changes needed.
- **Pluggable Storage** — Local filesystem for development, S3 /
  MinIO for production. Swap via config.

## Quick Start

### Prerequisites

- Docker & Docker Compose

### Local Development

```bash
make dev-all
```

Then open:

- Web UI: `http://localhost:3000`
- Backend API: `http://localhost:8080`

Local profile seeds two mock-auth users automatically:

- `local-user` for normal publishing and namespace operations
- `local-admin` with `SUPER_ADMIN` for review and admin flows

Use them with the `X-Mock-User-Id` header in local development.

Stop everything with:

```bash
make dev-all-down
```

Reset local dependencies and start from a clean slate with:

```bash
make dev-all-reset
```

Run `make help` to see all available commands.

### Container Runtime

Published runtime images are built by GitHub Actions and pushed to GHCR.
This is the supported path for anyone who wants a ready-to-use local
environment without building the backend or frontend on their machine.
Published images target both `linux/amd64` and `linux/arm64`.

1. Copy the runtime environment template.
2. Pick an image tag.
3. Start the stack with Docker Compose.

```bash
cp .env.release.example .env.release
```

Recommended image tags:

- `SKILLHUB_VERSION=edge` for the latest `main` build
- `SKILLHUB_VERSION=vX.Y.Z` for a fixed release

Start the runtime:

```bash
docker compose --env-file .env.release -f compose.release.yml up -d
```

Then open:

- Web UI: `http://localhost`
- Backend API: `http://localhost:8080`

Stop it with:

```bash
docker compose --env-file .env.release -f compose.release.yml down
```

The runtime stack uses its own Compose project name, so it does not
collide with containers from `make dev-all`.

The runtime uses the existing `local,docker` profile combination so it
is immediately usable with the same mock-auth flow as local development.
Available seeded users:

- `local-user`
- `local-admin`

Pass `X-Mock-User-Id` to the backend when you need an authenticated
session without configuring GitHub OAuth. If the GHCR package remains
private, run `docker login ghcr.io` before `docker compose up -d`.

## Architecture

```
┌─────────────┐     ┌─────────────┐     ┌──────────────┐
│   Web UI    │     │  CLI Tools  │     │  REST API    │
└──────┬──────┘     └──────┬──────┘     └──────┬───────┘
       │                   │                   │
       └───────────────────┼───────────────────┘
                           │
                    ┌──────▼──────┐
                    │   Nginx     │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │ Spring Boot │  Auth · RBAC · Core Services
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
       ┌──────▼───┐  ┌─────▼────┐  ┌───▼────┐
       │PostgreSQL│  │  Redis   │  │ Storage │
       └──────────┘  └──────────┘  └────────┘
```

## Contributing

Contributions are welcome. Please open an issue first to discuss
what you'd like to change.

## License

MIT
