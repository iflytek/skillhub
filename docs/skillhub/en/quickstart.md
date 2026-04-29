# Quick Start

This page keeps only the shortest startup path. Deeper publishing, discovery, review, scanning, and deployment topics are handled by the guide pages instead of being repeated here.

## One-Click Deployment

Use the curl command to deploy SkillHub with Web UI, Backend API, PostgreSQL, Redis, MinIO, and Skill Scanner:

```bash
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up
```

For users in China:

```bash
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up --aliyun
```

Common parameters:

| Parameter | Description | Example |
|-----------|-------------|---------|
| `--version <tag>` | Specify version | `--version v0.2.0` |
| `--aliyun` | Use Alibaba Cloud mirror | `--aliyun` |
| `--home <dir>` | Specify installation directory | `--home /opt/skillhub` |
| `--no-scanner` | Disable security scanning service | `--no-scanner` |

Other useful commands:

```bash
# Stop services
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- down

# Check status
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- ps

# View logs
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- logs
```

After deployment:

- Web UI: `http://localhost:3000`
- Backend API: `http://localhost:8080`
- API Docs: `http://localhost:8080/swagger-ui.html`
- Skill Scanner: `http://localhost:8000`

## Local Development

To run from source:

```bash
git clone https://github.com/iflytek/skillhub.git
cd skillhub
make dev-all
```

Typical prerequisites:

- Java 17+
- Docker & Docker Compose
- Node.js / pnpm for frontend work

If `make dev-all` fails, check:

1. Maven dependency downloads
2. `java -version`
3. Port conflicts on `8080` or `3000`

For more troubleshooting, see [FAQ](/en/faq).

## Logging In

Available options:

- Built-in admin account
  - Username: `admin`
  - Password: `ChangeMe!2026`
- Register a new account at `http://localhost:3000/register`
- Mock users for local development:

```bash
# Regular user
curl -H "X-Mock-User-Id: local-user" http://localhost:8080/api/v1/auth/me

# Super admin
curl -H "X-Mock-User-Id: local-admin" http://localhost:8080/api/v1/auth/me
```

Change the default admin password immediately after deploying to production.

## Next Steps

- [Introduction](/en/introduction)
- [Skill Publishing & Versioning](/en/guide/skill-publish)
- [Skill Search & Discovery](/en/guide/skill-discovery)
- [Namespace & Team Management](/en/guide/namespace)
- [Review & Governance](/en/guide/review)
- [Security Scanning](/en/guide/scanner)
- [Kubernetes Deployment](/en/guide/kubernetes)
- [FAQ](/en/faq)
