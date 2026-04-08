# Quick Start

## One-Click Deployment

Use the curl command to quickly deploy SkillHub (includes all services: Web UI, Backend API, PostgreSQL, Redis, MinIO, Skill Scanner):

```bash
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up
```

**For users in China (Alibaba Cloud mirror)**:
```bash
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up --aliyun
```

**Custom parameters**:
```bash
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up \
  --version v0.2.0 \
  --home /opt/skillhub \
  --aliyun
```

**Parameter reference**:
| Parameter | Description | Example |
|-----------|-------------|---------|
| `--version <tag>` | Specify version | `--version v0.2.0` |
| `--aliyun` | Use Alibaba Cloud mirror (recommended for China) | `--aliyun` |
| `--home <dir>` | Specify installation directory | `--home /opt/skillhub` |
| `--no-scanner` | Disable security scanning service | `--no-scanner` |
| `--mirror-registry <url>` | Custom image registry | `--mirror-registry registry.example.com` |

**Other commands**:
```bash
# Stop services
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- down

# Check service status
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- ps

# View logs
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- logs

# Clean all data
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- clean
```

After successful deployment, visit:
- **Web UI**: http://localhost:3000
- **Backend API**: http://localhost:8080
- **API Docs**: http://localhost:8080/swagger-ui.html
- **Skill Scanner**: http://localhost:8000

## Local Development

To start the development environment from source:

```bash
# Clone the repository
git clone https://github.com/iflytek/skillhub.git
cd skillhub

# Start all services (including Scanner)
make dev-all
```

### Notes for Developers in China

If `make dev-all` fails to start the backend, common causes include:

1. **Maven dependency download timeout**

   The project includes a built-in Aliyun mirror configuration (`server/.mvn/settings.xml`), but Maven does not automatically read project-level settings. You need to configure it manually:

   ```bash
   # Option 1: Copy to user directory (recommended)
   mkdir -p ~/.m2
   cp server/.mvn/settings.xml ~/.m2/settings.xml

   # Option 2: Specify on each build
   cd server && ./mvnw -s .mvn/settings.xml package
   ```

2. **Java version mismatch**

   SkillHub requires Java 21+:
   ```bash
   java -version
   ```

3. **Port conflict**

   Check if port 8080 is in use:
   ```bash
   lsof -i :8080
   ```

For detailed troubleshooting steps, see [FAQ](faq.md#local-development-startup-failure).

## Logging In

### Option 1: Use the Built-in Admin Account

SkillHub comes with a built-in super admin account for direct login:

- **Username**: `admin`
- **Password**: `ChangeMe!2026`

> **Security notice**: Change the default password immediately after deploying to production.

### Option 2: Register a New Account

Visit http://localhost:3000/register to create a new account.

### Option 3: Use Mock Users (Local Development Only)

During local development, you can use mock user headers for quick login:

```bash
# Regular user
curl -H "X-Mock-User-Id: local-user" http://localhost:8080/api/v1/auth/me

# Super admin
curl -H "X-Mock-User-Id: local-admin" http://localhost:8080/api/v1/auth/me
```

In the browser, you can add the `X-Mock-User-Id` header via a browser extension (e.g., ModHeader).

## Install the CLI Tool

SkillHub is compatible with the OpenClaw CLI. You can use the `npx clawhub` command to manage skill packages:

```bash
# Configure the SkillHub registry URL
export CLAWHUB_REGISTRY=http://localhost:8080

# Search for skill packages
npx clawhub search email

# Install a skill package
npx clawhub install my-skill

# Publish a skill package
npx clawhub publish ./my-skill
```

## Publish Your First Skill Package

### Publish via CLI (Recommended)

1. **Prepare the skill package**

Create a simple skill package directory:

```
my-skill/
├── skill.md          # Skill description
├── package.json      # Metadata
└── scripts/          # Script files
    └── main.py
```

2. **Publish using the CLI**

```bash
# Configure the registry
export CLAWHUB_REGISTRY=http://localhost:8080

# Publish to the default namespace
npx clawhub publish ./my-skill

# Publish to a specific namespace
npx clawhub publish ./my-skill --namespace my-team
```

3. **Wait for security scanning**

After publishing, the Skill Scanner will automatically scan the skill package for potential security issues:
- Malicious code detection
- Sensitive information leakage
- Dependency vulnerability scanning
- Behavioral analysis

Scan results are displayed on the skill package detail page.

4. **Wait for review** (if the namespace has review enabled)

Administrators will receive a notification and the skill package will be officially published once approved.

### Publish via Web UI

1. Visit http://localhost:3000/dashboard/publish
2. Select a namespace (create one first if needed)
3. Upload a zip file
4. Choose visibility (PUBLIC / PRIVATE / INTERNAL)
5. Click "Publish"

## Search and Download Skill Packages

### Using the CLI

```bash
# Search for skill packages
npx clawhub search pdf

# Install a skill package
npx clawhub install pdf-parser

# Install a skill package from a specific namespace
npx clawhub install my-team--pdf-parser
```

### Using the Web UI

1. Visit http://localhost:3000/search
2. Enter keywords to search
3. Click a skill package to view details
4. Click "Download" or copy the install command

## Upgrade SkillHub

Use the curl command to upgrade to the latest version:

```bash
# Upgrade to the latest version
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- pull
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- down
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up

# Upgrade to a specific version
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up --version v0.2.0
```

> **Note**: It is recommended to back up the database and object storage before upgrading.
