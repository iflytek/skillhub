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

> **Note**: It is recommended to back up the database and object storage before upgrading. Database migrations are handled automatically by Flyway.

## Q: What should I do if I encounter issues?

A: You can get help through the following channels:

- **GitHub Issues**: https://github.com/iflytek/skillhub/issues
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
