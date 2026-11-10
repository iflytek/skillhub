# Docker Compose Deployment and Upgrades

You do not need to deploy a server to install skills from an existing registry. Use this guide when hosting your own registry. `make dev-all` runs source development; `make staging` is for container regression testing; `compose.release.yml` is the single-server release deployment. For clusters, see [Kubernetes](kubernetes.html).

## One-click script deployment

The existing installer does not require SMTP configuration. Registry deployment and use remain unchanged when email is disabled. Use an explicit persistent directory for a long-lived instance:

```bash
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up \
  --home /opt/skillhub \
  --public-url https://skills.example.com
```

The directory and domain are examples. The operator must have write access to the directory and access to Docker. A public URL does not configure DNS, TLS, or a reverse proxy. Record the printed **Runtime dir** and **Lifecycle commands**. Reuse those commands for the existing instance rather than changing its directory to the example. Without `--home`, the default is `${TMPDIR:-/tmp}/skillhub-runtime`, not `~/.skillhub`.

The script downloads Compose and `.env.release.example`, and creates the private `.env.release` only when it does not exist. Existing SMTP credentials remain in that file. Runtime dir does not contain the repository's Makefile or validation tools; do not run the source-checkout-only `make validate-release-config` there.

| Operation | Procedure | Explanation |
|---|---|---|
| Change SMTP or other environment settings | Edit the existing Runtime dir's `.env.release`, then repeat that instance's up command | Changed environment values require container recreation; restart alone does not reload them |
| Update images | Keep the same home and source options, and use the target release's up command | pull downloads images without activating new containers |
| Pin a release | Use matching published script files, `--ref`, and `--version` | ref selects GitHub deployment files; version selects image tags; neither pins both |
| Inspect or stop | Use the printed ps, logs, and down commands | Do not add `-v` to down or use clean as a troubleshooting step |

Preserve `--aliyun`, mirror/image options, and any custom Compose configuration from the original deployment. With `--aliyun`, runtime files come from OSS; `--ref` alone does not prove those files match a Git tag. Check both deployment-file sources and image versions before upgrading.

### Configure business email

The target release must include the [business email changes](https://github.com/iflytek/skillhub/pull/1004). Disable business email in administration before changing SMTP. Edit the existing Runtime dir's `.env.release` with the SMTP host, account/app password, port, TLS mode, and an authorized sender address. Keep file permissions at `600`; do not commit or share it. Do not copy a fresh template over existing database, storage, or mail credentials.

Repeat the original up command and check backend health and SMTP configuration. A super administrator then sets the real site URL and digest window and enables business email during a coordinated verification window in **System configuration → Business email notifications**. An unverified test account can then verify its address in **Notification settings**; an already verified test account can trigger a new event to test immediate and digest delivery. Notification verification codes and business emails cannot be tested while the platform is disabled. Enabling is not recipient isolation: new events for existing eligible subscribers can also send email during that window. Disable the platform again on failure and inspect delivery status without blindly retrying UNKNOWN; after acceptance, restore test preferences and resume normal operation. Rotating SMTP credentials alone does not require all users to verify again; changing an account's email does. SMTP credentials belong to deployment configuration; persisted platform policy is managed in administration; user verification and preferences are separate. Environment edits do not replace persisted settings.

The updated installer removes empty legacy `SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_TRUST` overrides while preserving explicit host exceptions and certificate validation. For an older installer, remove the empty override manually before recreating the backend. Leave the variable unset by default; do not use `*`.

Acceptance includes receiving a real verification code, an immediate event, a combined digest for one window, and opening email details with the receiving SkillHub account. Configuration-present indicators and healthy containers do not prove inbox delivery. Upgrades preserve private configuration; deployment seed values do not overwrite saved platform policy. See the [email operations guide (Chinese)](https://github.com/iflytek/skillhub/blob/main/docs/notification-email.md) for parameters, initial setup, rotation, disabling, and failure diagnosis.

## Configure the instance

The following procedure is for a source checkout; one-click installations use the Runtime dir procedure above. Install Docker and Docker Compose, and obtain repository files matching your target release. From the repository root:

```bash
cp .env.release.example .env.release
```

Edit `.env.release` before starting:

| Setting | Check |
| --- | --- |
| `SKILLHUB_VERSION` | Pin a published release; `latest` tracks stable releases and `edge` tracks main-branch builds |
| `SKILLHUB_PUBLIC_BASE_URL` | The address users actually visit; use HTTPS in production |
| `WEB_PORT` / `API_PORT` | Defaults are 80 / 8080; check availability |
| `SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET` | Generate a random value of at least 32 characters; the placeholder prevents startup |
| `POSTGRES_PASSWORD` | Set your own database password |
| `BOOTSTRAP_ADMIN_ENABLED` / `BOOTSTRAP_ADMIN_PASSWORD` | Decide whether to initialize an administrator; replace the example password when enabled |
| Storage | Follow the template and the [object storage guide (Chinese)](https://github.com/iflytek/skillhub/blob/main/docs/object-storage.md) for external storage |

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

Suite review writes are enabled by default in the single-server release Compose topology; Suite Bundle confirmation is disabled by default. See [Bundle import](suite-bundle.html) for enabling it. For rolling upgrades where old and new backends coexist, follow the [Suite deployment order (Chinese)](https://github.com/iflytek/skillhub/blob/main/docs/25-skill-suites.md).

## Optional business-event OTLP Logs

Business analytics stays off by default and independent of tracing. Use `runtime.sh business-logs-enable`
for an existing receiver, or `business-logs-up` for an optional Collector with an explicit downstream.
For a local host Java process or container backend, use `make business-logs-demo-up`.
For a runtime release deployment, pass `--ref <tag> --version <tag>` together: ref selects files; version selects only images.
Metrics require the explicit `health,info,metrics,prometheus` exposure list and a server recreation.
The [operations guide](https://github.com/iflytek/skillhub/blob/main/docs/business-event-logs.md) covers both
paths, Helm Secrets, admin queries, troubleshooting and cleanup. Download preparation is not completion
or installation; this lossy stream is not an audit or billing system.

## CLI device authorization URL

`skillhub login --no-open --registry https://skills.example.com` first resolves `--token`, then `SKILLHUB_TOKEN`, then saved credentials for that registry. Device authorization starts only when no token is resolved; an invalid token does not automatically fall back. `--no-open` only disables automatic browser launch. Verify the resulting identity with `skillhub whoami --registry https://skills.example.com`.

- When `DEVICE_AUTH_VERIFICATION_URI` is unset, empty, or whitespace, the server appends `/device` to `SKILLHUB_PUBLIC_BASE_URL`, preserving sub-paths and trimming trailing slashes. For example, `https://skills.example.com/portal/` becomes `https://skills.example.com/portal/device`.
- Without a public base URL, the compatibility fallback is `/device`, resolved by the CLI against the registry origin. Set the actual public Web URL for production, sub-path, or separate Web/API deployments.
- An explicit valid HTTP(S) URL or root-relative path overrides the default. Invalid values prevent startup without echoing the configured value. `/cli/auth` is the legacy loopback callback, not the device-code entry page.

Edit `.env.release` in the existing deployment directory before recreating the backend:

- Source checkout: run `make validate-release-config` from the repository root containing `Makefile` and `scripts/validate-release-config.sh`, and resolve errors before recreation.
- One-click script installation: use the `Runtime dir` printed by the script (the instance's `--home` directory). The script downloads the Compose file and environment template, not the Make validation tooling; do not run `make` in that directory.

The following command applies to a standard stack without a custom project name or override files:

```bash
docker compose --env-file .env.release -f compose.release.yml up -d --no-deps --force-recreate server
```

If the original deployment uses `-p`, `COMPOSE_PROJECT_NAME`, or additional `-f` files, retain its project name, environment file and every `-f` argument in the original order. Do not copy the base command unchanged: it could target another instance or omit override settings. This only recreates the existing backend; it does not download deployment files again.

Use the full `up -d` for an initial deployment. A plain `restart` does not reload container environment variables. If other shared settings changed, recreate their affected services too. To verify the device URL, retry the login command without `--token`, `SKILLHUB_TOKEN`, or saved credentials for that registry, and check the returned public URL. Successful login with an existing token does not verify the device URL. Do not share codes or tokens. See the [operations reference (Chinese)](https://github.com/iflytek/skillhub/blob/main/docs/09-deployment.md) for the full configuration contract.

## Upgrade, stop, and troubleshoot

Before upgrading, record the current image version and configuration, and back up both the database and skill object storage. Set `SKILLHUB_VERSION` to the target release and repeat validation, `pull`, `up -d`, and workflow verification. Rolling back an image does not roll back the database; check migration compatibility first.

```bash
# Stop this instance; do not add -v unless deliberately deleting data volumes
docker compose --env-file .env.release -f compose.release.yml down
```

For startup failures, inspect `ps` and service logs, then check database, Redis, storage, and public URL configuration. Include image versions, redacted logs, and request IDs when reporting a problem, never credentials or `.env.release`. See the [operations reference (Chinese)](https://github.com/iflytek/skillhub/blob/main/docs/09-deployment.md) for Redis, reverse proxies, collection services, and migrations.

## Customize branding and the homepage

Use a release that includes site presentation settings. The one-command deployment entry and `.env.release` need no new branding variables. New installations retain the existing appearance. A **SUPER_ADMIN** manages instance settings in **System configuration → Site presentation**.

| Group | Options | Defaults and boundaries |
| --- | --- | --- |
| Branding | Display name, logo and favicon | Empty overrides use SkillHub and built-in images; API names, CLI commands and skill coordinates do not change |
| Homepage | Landing or catalog mode | Landing retains the current composition; catalog emphasizes search and existing skill sections |
| Hero text | Localized title and description | English, Chinese, Korean and Russian; missing translations use the editor's documented fallback |
| Sections | Popular, latest and quick start | Visibility does not grant access or disable business APIs |
| Footer | Show or hide | Shown by default |

### Edit, preview and save

1. Load the current configuration and edit the required fields.
2. Upload PNG or JPEG images, no larger than 2 MiB and 1024 pixels on either side. The server validates and re-encodes them as PNG. Favicons also use PNG; SVG, ICO and WebP are not accepted.
3. Preview the draft without changing the public site. Restoring a group to defaults also changes only the draft.
4. Save the configuration. The current client refreshes its presentation; other open clients receive it when configuration is fetched again or the page is refreshed. No image rebuild or container restart is required.
5. Failed saves retain the draft. A version conflict means another administrator saved first: load the latest settings, compare them and reapply the intended changes.

Navigation warns about unsaved edits. Settings support plain text and controlled brand assets, not custom HTML, CSS or scripts. Personal theme and language choices remain independent.

### Upgrades, backups and troubleshooting

Settings use the existing PostgreSQL `system_setting` table. Images use the instance's configured file storage. Keep the same database and storage when upgrading; startup does not overwrite administrator choices. Back up and restore both database and storage volumes/objects. A database-only restore can leave image references without their objects. Do not reset volumes to recover default branding. Reverting an application image is not a database restore.

| Symptom | Check and recovery |
| --- | --- |
| Editor unavailable or save denied | Verify the account has SUPER_ADMIN; ordinary members can only read public presentation |
| Upload rejected | Check actual file type, 2 MiB size and 1024-pixel dimensions, not just the extension |
| Another browser still shows old settings | Refresh configuration and confirm both clients use the same instance |
| Logo cannot load | Inspect the brand asset request and persistent storage; the UI falls back to the built-in image |
| Configuration cannot load | Inspect the public settings request and backend logs, repair connectivity and reload; default fallback does not mean stored overrides were deleted |

## Configuration and acceptance checks

Use the release template and Compose file from the same version. `.env.release` supplies Compose interpolation values; only declared variables reach the process. Wire additional settings explicitly through your own override. Recreate affected services after changes; `restart` does not reload container environment values.

| Layer | Observable result |
| --- | --- |
| Configuration | Validation has no errors; inspect each warning without sharing secret-bearing output |
| Dependencies and processes | Services run in `ps`; server logs show no repeating connection or migration failures |
| Public entry | Browser login works at the final URL; CLI `whoami` works against that registry |
| Business and persistence | Publish a test skill, follow its final state, install its exact version and inspect `SKILL.md`; resources remain usable after restart |
| Runtime dependencies | Package storage is readable/writable and an actual scan completes, not merely the scanner health probe |

Set `SESSION_COOKIE_SECURE=true` for production HTTPS. Sub-path deployments require aligned `SKILLHUB_WEB_BASE_PATH=/skillhub/`, same-origin `SKILLHUB_WEB_API_BASE_URL=/skillhub`, and a public URL ending in `/skillhub`. The public URL does not configure TLS certificates, proxying or DNS for you.

| Symptom | First checks |
| --- | --- |
| Configuration rejected | Example passwords, anonymous-download secret length, storage provider and required S3 settings |
| Page loads but API returns 502 | Server logs, migrations, API upstream and container networking |
| Wrong OAuth/device-flow URL | Public URL, domain and sub-path alignment |
| Changes have no effect | Correct Compose project, preserved overrides, explicit environment wiring and recreation |
| Scan never finishes | Scanner logs/configuration and backend connectivity; failure is not approval |

Back up PostgreSQL, skill/avatar storage, private configuration and encryption keys. Restore with matching application/configuration versions and verify login and download in an isolated environment. Deleting volumes or clearing the database is not a default upgrade-recovery step.
