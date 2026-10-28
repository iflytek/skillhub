# Docker Compose Deployment and Upgrades

You do not need to deploy a server to install skills from an existing registry. Use this guide when hosting your own registry. `make dev-all` runs source development; `make staging` is for container regression testing; `compose.release.yml` is the single-server release deployment. For clusters, see [Kubernetes](kubernetes.md).

## Configure the instance

Install Docker and Docker Compose, and obtain repository files matching your target release. From the repository root:

```bash
cp .env.release.example .env.release
```

Edit `.env.release` before starting:

| Setting | Check |
| --- | --- |
| `SKILLHUB_VERSION` | Pin a published release; `latest` tracks stable releases and `edge` tracks main-branch builds |
| `SKILLHUB_PUBLIC_BASE_URL` | The address users actually visit; use HTTPS in production |
| `WEB_PORT` / `API_PORT` | Defaults are 80 / 8080; check availability |
| `POSTGRES_PASSWORD` | Set your own database password |
| `BOOTSTRAP_ADMIN_ENABLED` / `BOOTSTRAP_ADMIN_PASSWORD` | Decide whether to initialize an administrator; replace the example password when enabled |
| Storage | Follow the template and the [object storage guide](https://github.com/iflytek/skillhub/blob/main/docs/object-storage.md) for external storage |

Do not commit or share `.env.release`. Changing an environment variable does not rotate the password of an existing database. Do not enable the `local` profile's mock authentication in a release deployment.

## Start and verify

```bash
make validate-release-config
docker compose --env-file .env.release -f compose.release.yml pull
docker compose --env-file .env.release -f compose.release.yml up -d
docker compose --env-file .env.release -f compose.release.yml ps
docker compose --env-file .env.release -f compose.release.yml logs --tail=100 server
```

Resolve validation errors before starting. Backend startup runs Flyway database migrations. Open your configured public URL, sign in with your configured administrator credentials, and verify search, download, and an authorized publishing/review flow. Healthy containers alone do not prove those workflows work.

Suite review writes are enabled by default in the single-server release Compose topology; Suite Bundle confirmation is disabled by default. See [Bundle import](suite-bundle.md) for enabling it. For rolling upgrades where old and new backends coexist, follow the [Suite deployment order](https://github.com/iflytek/skillhub/blob/main/docs/25-skill-suites.md).

## Upgrade, stop, and troubleshoot

Before upgrading, record the current image version and configuration, and back up both the database and skill object storage. Set `SKILLHUB_VERSION` to the target release and repeat validation, `pull`, `up -d`, and workflow verification. Rolling back an image does not roll back the database; check migration compatibility first.

```bash
# Stop this instance; do not add -v unless deliberately deleting data volumes
docker compose --env-file .env.release -f compose.release.yml down
```

For startup failures, inspect `ps` and service logs, then check database, Redis, storage, and public URL configuration. Include image versions, redacted logs, and request IDs when reporting a problem, never credentials or `.env.release`. See the [operations reference](https://github.com/iflytek/skillhub/blob/main/docs/09-deployment.md) for Redis, reverse proxies, collection services, and migrations.
