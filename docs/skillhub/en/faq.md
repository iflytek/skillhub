# FAQ

## Q: What is the difference between SkillHub and ClawHub?

A: SkillHub is an enterprise-grade, self-hosted solution that provides stronger access control, review mechanisms, and governance capabilities. ClawHub is a public registry, similar to npm.

**Key Differences**:

| Feature | SkillHub | ClawHub |
|------|----------|---------|
| **Deployment** | Self-hosted | Public cloud |
| **Access Control** | Namespace RBAC | Basic permissions |
| **Review Mechanism** | Multi-level review | None |
| **Security Scanning** | Built-in Skill Scanner | None |
| **Data Sovereignty** | Fully self-managed | Hosted in the cloud |
| **Use Case** | Enterprise internal | Public sharing |

## Q: How do I back up data?

A: SkillHub stores data in PostgreSQL and object storage. Regularly backing up these two components is sufficient.

**Back up PostgreSQL**:
```bash
pg_dump -h localhost -U postgres skillhub > backup.sql
```

**Back up Object Storage**:
- If using MinIO, back up the MinIO data directory
- If using S3, use the AWS CLI or an S3 backup tool

## Q: What authentication methods are supported?

A: SkillHub supports multiple authentication methods:

- **OAuth2**: GitHub, Google, GitLab, etc.
- **Local Accounts**: Username/password login (built-in administrator: admin / ChangeMe!2026)
- **Enterprise SSO**: Integrates with LDAP, SAML, etc.

Refer to the authentication configuration section in the project README for setup instructions.

## Q: Is there a size limit for skill packages?

A: The default limit is **100MB**. This can be adjusted via configuration:

```yaml
# application.yml
spring:
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB
```

## Q: How do I use the CLI tool to manage skill packages?

A: SkillHub is compatible with the OpenClaw CLI. Use the `npx clawhub` command to interact with it:

```bash
# Configure the registry URL
export CLAWHUB_REGISTRY=http://your-skillhub-host:8080

# Search for skill packages
npx clawhub search email

# Install a skill package
npx clawhub install my-skill

# Publish a skill package
npx clawhub publish ./my-skill
```

## Q: How do I configure HTTPS?

A: For production environments, it is recommended to use Nginx or Traefik as a reverse proxy with SSL certificates.

**Nginx Configuration Example**:
```nginx
server {
    listen 443 ssl;
    server_name skillhub.example.com;
    
    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;
    
    location / {
        proxy_pass http://localhost:3000;
    }
    
    location /api {
        proxy_pass http://localhost:8080;
    }
}
```

## Q: How do I monitor SkillHub?

A: SkillHub provides several monitoring options:

- **Health Check**: `GET /actuator/health`
- **Scanner Health Check**: `GET http://localhost:8000/health`
- **Metrics**: `GET /actuator/metrics` (Prometheus format)
- **Audit Logs**: All critical operations are recorded in the audit log
- **Application Logs**: Collect logs using ELK or Loki

## Q: Does it support multi-tenancy?

A: SkillHub achieves logical multi-tenant isolation through namespaces. Each namespace acts as a tenant with its own members, permissions, and skill packages.

For physical isolation, you can deploy a separate SkillHub instance for each tenant.

## Q: How do I upgrade SkillHub?

A: Use the curl command to upgrade:

```bash
# Pull the latest images and restart
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- pull
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- down
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up

# Or upgrade to a specific version
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up --version v0.2.0
```

> **Note**: It is recommended to back up the database and object storage before upgrading. Database migrations are handled automatically by Flyway. Upgrading does not wipe the database, so already-registered skill packages will not be lost.

## Q: Why can't administrators (admin) and regular users create namespaces?

A: Older versions of SkillHub do not support creating namespaces, as this feature was introduced in later updates. Please upgrade your SkillHub instance to the latest version (`latest`).
Upgrade command example:
```bash
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up --version latest
```

## Q: How do I search for or operate on a skill package within a specific namespace?

A: When using the OpenClaw CLI, you can specify the namespace using the `<namespace>--<skill-name>` format for operations like search or installation. If you encounter issues finding it on the web interface, you can also manage it by exporting the skill package and importing it into your target namespace.

## Q: What is the recommended deployment method? Can I pull the images and deploy manually?

A: We recommend the official one-line deployment script. Pulling images and deploying manually is not recommended (manual deployment is prone to initialization issues such as being redirected back to the login page after logging in):

```bash
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up --aliyun --public-url https://skillhub.your-company.com --version latest
```

The script performs a series of initialization steps. The generated runtime configuration is located at `/tmp/skillhub-runtime/` by default (containing `.env.release` and the docker-compose file).

## Q: After deployment, I enter the correct username and password but get redirected back to the login page?

A: This is most commonly seen with **manual deployment** (caused by API errors or incomplete initialization). Suggestions:

1. Switch to the one-line script above for deployment.
2. If necessary, clear and recreate the PostgreSQL data volume, then log in again.
3. If a reverse proxy is in front, verify that it forwards requests correctly.

## Q: How do I change the admin password? Why don't my config changes take effect?

A: Environment variables are injected when a container is created, so you must recreate the containers after changing them; `restart` alone does not re-inject environment variables.

1. Edit `/tmp/skillhub-runtime/.env.release` in the runtime directory (refer to [.env.release.example](https://github.com/iflytek/skillhub/blob/main/.env.release.example)).
2. Recreate the relevant containers:

   ```bash
   docker compose \
     --env-file /tmp/skillhub-runtime/.env.release \
     -f /tmp/skillhub-runtime/compose.release.yml \
     up -d --force-recreate
   ```

3. If the password was already persisted to the database and the change still doesn't take effect, you may need to clear the corresponding data and re-initialize.

## Q: Is an email verification code required to change / reset a password?

A: Yes. By default, passwords are changed or reset via an email verification code, so SMTP must be configured first. See [docs/19-smtp-password-reset-email-setup.md](https://github.com/iflytek/skillhub/blob/main/docs/19-smtp-password-reset-email-setup.md). Administrators can also reset it via `.env.release`.

## Q: Can a skill have a Chinese name?

A: Skill names are generally in English; Chinese names are not currently supported (using a Chinese skill name in OpenClaw will cause an error).

## Q: Can unreviewed skills be downloaded?

A: As long as you have permission to view it, it can generally be downloaded.

## Q: How do I hide or remove the GitHub / GitLab SSO login options on the login page?

A: Edit `application.yml` and comment out or delete the `github` and `gitlab` blocks under `spring.security.oauth2.client.registration`, along with their corresponding `provider` sections. Spring Boot then won't create these registrations at startup, and the login page won't show those entries.

## Q: Is SkillHub's security scanning (Skill Scanner) developed in-house by iFLYTEK? What license does it use?

A: SkillHub has built-in security scanning. The scanner integration, task orchestration, audit persistence, and deployment integration are implemented by the iFLYTEK team; the underlying scanning service uses Cisco's [cisco-ai-skill-scanner](https://github.com/cisco-ai-defense/skill-scanner) (Apache License 2.0, copyright Cisco).

## Q: Which version of cisco-ai-skill-scanner does SkillHub use?

A: `scanner/Dockerfile` runs `pip install cisco-ai-skill-scanner` directly without pinning a version, so the latest version on PyPI is pulled when the image is built. To pin a version, do so yourself when customizing the build.

## Q: How do I troubleshoot a `registry returned 400` error from `skillhub publish` (CLI)?

A: A 400 usually means backend validation failed. Common causes:

- `SKILL.md` is not in the package root directory;
- `SKILL.md` frontmatter is missing `name` / `description` or is malformed;
- name or version conflict (e.g. `error.skill.publish.nameConflict`, meaning a skill with the same name is already published in that namespace) — change `name` in `SKILL.md`, use another namespace, or have an admin handle the existing skill;
- the namespace does not exist, or you are not a member of it;
- the package contains suspected tokens/secrets that the CLI cannot confirm skipping;
- file type / size / path is not allowed.

You can inspect the server logs to locate the cause:

```bash
docker logs --tail=300 <skillhub-server container> 2>&1 | grep -Ei 'publish|SKILL.md|namespace|400|BadRequest'
```

## Q: What directory structure does a skill package require?

A: The package root directory must contain a `SKILL.md` file, whose frontmatter must include fields such as `name` and `description`.

## Q: Publishing fails with "package validation failed / malformed input" — what do I do?

A: This error occurs while unzipping and reading file names, usually because the archive is not UTF-8 encoded (e.g. created with the built-in Windows compression tool) or contains Chinese/non-ASCII paths. Repackage using UTF-8 encoding and avoid Chinese / special-character paths.

## Q: How many files can a skill package contain? What if I hit the file-count limit?

A: The default limit is **100 files** (this is separate from the 100MB size limit). To raise it, change the `skillhub.publish.max-file-count` setting, or override it via an environment variable at deploy time:

```bash
SKILLHUB_PUBLISH_MAX_FILE_COUNT=500
```

Recreate the containers for the change to take effect; `restart` alone does not re-inject environment variables. Note that `compose.release.yml` must also reference this variable; older versions (e.g. v0.2.6) may hard-code the value, so upgrading to the latest version is recommended.

## Q: Is there a server version requirement for using the CLI (publish / download, etc.)?

A: A SkillHub server image of **v0.2.7 or later** is required for CLI features.

## Q: Does SkillHub support MySQL?

A: Currently only PostgreSQL is supported; MySQL is not supported.

## Q: Can SkillHub be used to distribute Plugins?

A: Not supported for now.

## Q: How do I check the SkillHub version? How do I customize it (e.g. change the logo)?

A:

- Check the server image version:

```bash
docker image inspect ghcr.io/iflytek/skillhub-server:latest --format '{{index .Config.Labels "org.opencontainers.image.version"}}'
```

- Check the CLI version: `skillhub version`.
- For customization (e.g. changing the logo), it is recommended to fork the latest code, modify it, and build your own Docker image.

## Q: The page loads, but the login / register APIs return 502?

A: The page is served by the `web` container, while login, register and other APIs are proxied by `web` to `server` (default `SKILLHUB_API_UPSTREAM=http://server:8080`). When the page works but the API returns 502, check whether `server` started correctly first; a wrong upstream, DNS, or container-network problem can also produce a 502.

Troubleshooting order:

```bash
# 1. Check whether server is running
docker compose --env-file .env.release -f compose.release.yml ps

# 2. Look at the first error in the server startup log
docker compose --env-file .env.release -f compose.release.yml logs server | head -50
```

One common startup failure is:

```
SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET must not use the default placeholder
```

This means `server` still reads the placeholder from the template. Replace it in `.env.release` with your own random string (**at least 32 characters**) and recreate the containers:

```bash
SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET=<your own random string, at least 32 characters>
```

Running `make validate-release-config` before startup validates `.env.release` and surfaces placeholders and missing values early.

## Q: Why doesn't my configuration change take effect?

A: Two common causes:

1. **Edited the wrong file**: `.env.release.example` is only a template; Compose reads the file passed via `--env-file`, i.e. `.env.release`. Run `cp .env.release.example .env.release` first, then edit `.env.release`.
2. **Restarted instead of recreated**: environment variables are injected when the container is created, and `restart` does not re-inject them. Recreate the containers after a config change:

```bash
docker compose --env-file .env.release -f compose.release.yml up -d --force-recreate
```

## Q: What external dependencies does SkillHub require at runtime?

A: PostgreSQL and Redis are required. Object storage supports both `local` and S3, controlled by `SKILLHUB_STORAGE_PROVIDER`. `.env.release.example` explicitly selects `local`, but if the variable is completely unset when using `compose.release.yml`, the Compose fallback is `s3`. Set it explicitly; S3 is recommended for production (configured via `SKILLHUB_STORAGE_S3_*`). Only PostgreSQL is supported as the database — MySQL is not.

The release Compose file already bundles PostgreSQL and Redis, bound to `127.0.0.1` by default.

## Q: How does an account created through OAuth (GitHub / GitLab, etc.) get admin rights?

A: The first OAuth login creates a regular user. An existing `SUPER_ADMIN` (for example the bootstrap admin created during initialization) has to promote it from the admin console.

A `USER_ADMIN` can manage user status and assign platform roles other than `SUPER_ADMIN`, but cannot grant `SUPER_ADMIN` to any account or change the role of an existing `SUPER_ADMIN`. Only a `SUPER_ADMIN` can perform those two operations.

## Q: How do I install multiple skills in bulk?

A: The CLI `install` command handles one skill at a time. Both examples below use `--dir` to install the skills under the same target root; each skill is placed in `$target_dir/<skill-slug>/`:

```bash
target_dir=/opt/skillhub-skills

# install one by one
for skill in skill-a skill-b skill-c; do
  skillhub install "$skill" --dir "$target_dir"
done

# or read from a manifest file (one skill name per line)
xargs -a skills.txt -I {} skillhub install "{}" --dir "$target_dir"
```

Since **SkillHub Server v0.2.12**, public skills support anonymous search and install. Note that an invalid bearer token now fails the command instead of falling back to anonymous access — update or remove the stale credential in that case.

## Q: Why does the account merge page say that merging is temporarily unavailable?

A: The legacy account merge flow could not independently prove control of the primary and
secondary accounts, so it has been isolated as a security measure. Until the replacement
double-reauthentication flow is available:

- Continue using the two accounts separately.
- Do not ask an administrator to edit the database or manually move identity bindings, roles,
  namespace memberships, local credentials, or API tokens.
- Existing `account_merge_request` rows are retained but cannot be completed.
- Authenticated API clients calling the legacy routes receive `503 Service Unavailable`.

Normal login, existing identity binding lookup, namespace operations, and skill operations are not
affected.

## Q: What should I do if I encounter issues?

A: You can get help through the following channels:

- **GitHub Issues**: https://github.com/iflytek/skillhub/issues
- **Online Docs**: https://iflytek.github.io/skillhub/
- **Documentation**: Refer to the project README.md
- **Community Discussions**: https://github.com/iflytek/skillhub/discussions

## Q: What should I do if local development fails to start?

A: When `make dev-all` fails to start the backend, detailed error messages will be displayed. Common issues:

### 1. Maven dependency download failed (network timeout)

**Symptoms**: Backend logs show `Could not transfer artifact` or connection timeout

**Solution**: Configure Aliyun mirror

```bash
# Copy the project's built-in mirror configuration to user directory
mkdir -p ~/.m2
cp server/.mvn/settings.xml ~/.m2/settings.xml
```

Or manually create `~/.m2/settings.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <url>https://maven.aliyun.com/repository/public</url>
      <mirrorOf>central</mirrorOf>
    </mirror>
  </mirrors>
</settings>
```

Reference: [Aliyun Maven Mirror Configuration Guide](https://maven.aliyun.com/mvn/guide)

### 2. Java version mismatch

**Symptoms**: `Unsupported class file major version` or `java.lang.NoSuchMethodError`

**Solution**: Install Java 21+

```bash
# macOS
brew install openjdk@21

# Verify version
java -version
```

### 3. Port already in use

**Symptoms**: `Port 8080 already in use`

**Solution**:

```bash
# Find the process using the port
lsof -i :8080

# Terminate the process
kill -9 <PID>
```

### 4. View detailed logs

If the above solutions don't help, check the backend logs:

```bash
make dev-logs SERVICE=backend
# Or view directly
cat .dev/server.log
```
