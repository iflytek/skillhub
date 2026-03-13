# SkillHub

An enterprise-grade, open-source agent skill registry — publish, discover, and
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
  platform admins gate promotions to the global scope. Governance
  actions are audit-logged for compliance.
- **Social Features** — Star skills, rate them, and track downloads.
  Build a community around your organization's best practices.
- **Account Merging** — Consolidate multiple OAuth identities and
  API tokens under a single user account.
- **API Token Management** — Generate scoped tokens for CLI and
  programmatic access with prefix-based secure hashing.
- **CLI-First** — Native REST API plus a compatibility layer for
  existing ClawHub-style registry clients. Native CLI APIs are the
  primary supported path while protocol compatibility continues to
  expand.
- **Pluggable Storage** — Local filesystem for development, S3 /
  MinIO for production. Swap via config.
- **Internationalization** — Multi-language support with i18next.

## Quick Start

Start the full local stack with: `curl -fsSL https://raw.githubusercontent.com/iflytek/skillhub/main/scripts/runtime.sh | sh -s -- up`

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

### API Contract Sync

OpenAPI types for the web client are checked into the repository.
When backend API contracts change, regenerate the SDK and commit the
updated generated file:

```bash
make generate-api
```

For a stricter end-to-end drift check, run:

```bash
./scripts/check-openapi-generated.sh
```

This starts local dependencies, boots the backend, regenerates the
frontend schema, and fails if the checked-in SDK is stale.

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
make validate-release-config
docker compose --env-file .env.release -f compose.release.yml up -d
```

Then open:

- Web UI: `SKILLHUB_PUBLIC_BASE_URL` 对应的地址
- Backend API: `http://localhost:8080`

Stop it with:

```bash
docker compose --env-file .env.release -f compose.release.yml down
```

The runtime stack uses its own Compose project name, so it does not
collide with containers from `make dev-all`.

The production Compose stack now defaults to the `docker` profile only.
It does not enable local mock auth. Instead, the backend bootstraps a
local admin account from environment variables for the first login:

- username: `BOOTSTRAP_ADMIN_USERNAME`
- password: `BOOTSTRAP_ADMIN_PASSWORD`

Recommended production baseline:

- set `SKILLHUB_PUBLIC_BASE_URL` to the final HTTPS entrypoint
- keep PostgreSQL / Redis bound to `127.0.0.1`
- use external S3 / OSS via `SKILLHUB_STORAGE_S3_*`
- rotate or disable the bootstrap admin after initial setup
- run `make validate-release-config` before `docker compose up -d`

If the GHCR package remains private, run `docker login ghcr.io` before
`docker compose up -d`.

### Monitoring

A Prometheus + Grafana monitoring stack lives under [`monitoring/`](./monitoring).
It scrapes the backend's Actuator Prometheus endpoint.

Start it with:

```bash
cd monitoring
docker compose -f docker-compose.monitoring.yml up -d
```

Then open:

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3001` (`admin` / `admin`)

By default Prometheus scrapes `http://host.docker.internal:8080/actuator/prometheus`,
so start the backend locally on port `8080` first.

## Kubernetes

Basic Kubernetes manifests are available under [`deploy/k8s/`](./deploy/k8s):

- `configmap.yaml`
- `secret.yaml.example`
- `backend-deployment.yaml`
- `frontend-deployment.yaml`
- `services.yaml`
- `ingress.yaml`

Apply them after creating your own secret:

```bash
kubectl apply -f deploy/k8s/configmap.yaml
kubectl apply -f deploy/k8s/secret.yaml
kubectl apply -f deploy/k8s/backend-deployment.yaml
kubectl apply -f deploy/k8s/frontend-deployment.yaml
kubectl apply -f deploy/k8s/services.yaml
kubectl apply -f deploy/k8s/ingress.yaml
```

## Smoke Test

A lightweight smoke test script is available at [`scripts/smoke-test.sh`](./scripts/smoke-test.sh).

Run it against a local backend:

```bash
./scripts/smoke-test.sh http://localhost:8080
```

## Architecture

```
┌─────────────┐     ┌─────────────┐     ┌──────────────┐
│   Web UI    │     │  CLI Tools  │     │  REST API    │
│  (React 19) │     │             │     │              │
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
                    │   (Java 21) │  OAuth2 · API Tokens · Audit
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
       ┌──────▼───┐  ┌─────▼────┐  ┌────▼────┐
       │PostgreSQL│  │  Redis   │  │ Storage │
       │    16    │  │    7     │  │ S3/MinIO│
       └──────────┘  └──────────┘  └─────────┘
```

**Backend (Spring Boot 3.2.3, Java 21):**
- Multi-module Maven project with clean architecture
- Modules: app, domain, auth, search, storage, infra
- PostgreSQL 16 with Flyway migrations
- Redis for session management
- S3/MinIO for skill package storage

**Frontend (React 19, TypeScript, Vite):**
- TanStack Router for routing
- TanStack Query for data fetching
- Tailwind CSS + Radix UI for styling
- OpenAPI TypeScript for type-safe API client
- i18next for internationalization

## Contributing

Contributions are welcome. Please open an issue first to discuss
what you'd like to change.

- Contribution guide: [`CONTRIBUTING.md`](./CONTRIBUTING.md)
- Code of conduct: [`CODE_OF_CONDUCT.md`](./CODE_OF_CONDUCT.md)

## License

Apache License 2.0
