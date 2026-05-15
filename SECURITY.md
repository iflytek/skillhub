# Security Policy

## Reporting a Vulnerability

**Do not open public GitHub issues for security vulnerabilities.**

To report a security issue, use one of the following private channels:

- **GitHub Security Advisories**: [https://github.com/iflytek/skillhub/security/advisories/new](https://github.com/iflytek/skillhub/security/advisories/new)
- **Email**: Send a detailed report to the maintainers via private channels listed in the repository's `CODE_OF_CONDUCT.md`

Please include the following in your report:

- A clear description of the vulnerability and its impact
- The affected component(s) and version(s)
- Steps to reproduce or a proof-of-concept
- Any suggested mitigations or fixes

We will acknowledge your report within **5 business days** and aim to provide an initial assessment within **14 business days**. We will keep you informed of remediation progress and coordinate disclosure timing with you.

## Supported Versions

| Version | Supported |
|---------|-----------|
| `latest` (stable release) | ✅ |
| `edge` (latest `main` build) | ⚠️ Best-effort only |
| Older releases | ❌ |

Security fixes are shipped in the next stable release. We do not backport fixes to older versions.

## Security Architecture

SkillHub is a self-hosted agent skill registry. The security model is designed around the assumption that the platform runs behind an organization's firewall and is trusted-internal by default, with multiple authorization layers for defense in depth.

### Authentication

| Mechanism | Scope |
|-----------|-------|
| Session-based (Spring Security + Redis) | Browser UI |
| OAuth 2.0 / OIDC (GitHub, GitLab, generic OIDC) | Browser SSO |
| Local credentials (username + bcrypt-hashed password) | Bootstrap / non-SSO setups |
| API Tokens (SHA-256 hashed, prefix-based) | CLI and programmatic access |
| Device Authorization (RFC 8628) | Headless CLI login |

Session fixation is mitigated via session ID rotation on login (`changeSessionId()`). API tokens use a `sk_` prefix for leak detection and are stored as SHA-256 hashes — the raw token is shown only once at creation time.

### Authorization

SkillHub enforces a **two-layer authorization model**:

1. **Web-layer route policies** (`RouteSecurityPolicyRegistry.AUTHORIZATION_POLICIES`): URL-pattern → access-level mapping (public, authenticated, role-protected).
2. **API Token scope policies** (`RouteSecurityPolicyRegistry.API_TOKEN_POLICIES`): fine-grained scope checks applied on top of route policies when the request is authenticated via API Token (e.g., `skill:publish`, `skill:delete`, `token:manage`).

Domain services additionally enforce **namespace membership and role checks** before mutating resources (publish, review, governance actions).

### CSRF

All `/api/` prefix routes skip Spring Security CSRF protection. This is intentional for stateless API access. Browser-based mutation requests authenticate via session cookies; CSRF is mitigated by the `SameSite=Lax` cookie attribute enforced by Spring Session.

### Security Headers

| Header | Value |
|--------|-------|
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `X-XSS-Protection` | `0` (disabled; modern browsers handle XSS natively) |
| `Content-Security-Policy` | `default-src 'self'` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=()` |

### Skill Package Security

Uploaded skill packages (ZIP archives) go through a multi-stage security pipeline:

1. **Path traversal prevention** (Zip Slip): All ZIP entry paths are normalized and validated via `SkillPackagePolicy.normalizeEntryPath()`, which rejects absolute paths, `..` segments, drive prefixes, and non-canonical paths.
2. **File size limits**: 10 MB per file, 100 MB total package, 500 files max.
3. **Extension whitelist**: Only document, code, image, and Office formats are allowed.
4. **Content-type validation**: Binary formats (PNG, JPG, GIF, SVG, PDF) are verified by magic-byte inspection, not just extension.
5. **OS metadata filtering**: `__MACOSX/`, `.DS_Store`, and `._*` entries are skipped.
6. **Security scanning** (optional): When `SKILLHUB_SECURITY_SCANNER_ENABLED=true`, newly published versions enter a `SCANNING` state and are analyzed by the external `skill-scanner` service before review. Scanner failures fall through to the human review path to avoid permanent blocking.

### Audit Logging

All significant operations (publish, review, governance actions, namespace mutations) are recorded in an audit log with: operator ID, action type, target entity, request ID, client IP, User-Agent, and structured details (JSON).

## Known Security Considerations

The following are documented areas that operators and contributors should be aware of. They represent design trade-offs or areas under active improvement rather than unmitigated critical vulnerabilities.

### Bootstrap Admin Account

The `BootstrapAdminInitializer` creates a default admin account on first startup when `BOOTSTRAP_ADMIN_ENABLED=true` (the default for Docker-based deployments). The default credentials are:

- Username: `admin`
- Password: `ChangeMe!2026`

**Action required**: Change this password or set `BOOTSTRAP_ADMIN_ENABLED=false` before exposing SkillHub to any untrusted network. The `.env.release.example` file includes this as a reminder.

### Download Count Integrity

Download counters are incremented on each successful download using atomic SQL (`download_count = download_count + 1`), which avoids read-then-write race conditions. However, there is no deduplication by IP or user ID, so repeated downloads from the same client will inflate the count. If download metrics are used for trust decisions, consider implementing rate-limited or deduplicated counting.

### Audit Log Detail JSON Construction

Several service methods build `detailJson` strings via concatenation and `replace("\"", "\\\"")` rather than using `ObjectMapper`. While the current risk is limited (the values are stored, not parsed back for security decisions), the escaping is incomplete (backslash itself is not escaped). This is tracked for remediation — new code should use `ObjectMapper.writeValueAsString()` for all JSON construction.

### Controller-Layer Authorization Depth

Some controller endpoints rely solely on route-level `authenticated` checks without `@PreAuthorize` annotations. The domain service layer provides the actual authorization (namespace membership, role checks). While this is secure today, adding `@PreAuthorize` at the controller layer would provide defense in depth against future refactoring that might bypass the domain service.

### API Token Scope Coverage

API Token scope policies use a deny-by-default model: any path not explicitly listed in `API_TOKEN_POLICIES` returns `unsupported` for token-authenticated requests. New API endpoints must be added to the policy registry to work with API tokens.

## Deployment Security Checklist

Before running SkillHub in a production or externally accessible environment:

- [ ] **Change default passwords**: Set strong passwords for `BOOTSTRAP_ADMIN_PASSWORD`, `POSTGRES_PASSWORD`, and Redis (if auth is enabled).
- [ ] **Disable bootstrap admin**: Set `BOOTSTRAP_ADMIN_ENABLED=false` after initial setup.
- [ ] **Configure OAuth/OIDC**: Replace mock-auth with a real identity provider (`SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_*`).
- [ ] **Enable session cookie security**: Set `SESSION_COOKIE_SECURE=true` when serving over HTTPS.
- [ ] **Use S3/MinIO storage**: Switch `SKILLHUB_STORAGE_PROVIDER=s3` and configure credentials. Local file storage (`local`) is for development only.
- [ ] **Enable security scanner**: Set `SKILLHUB_SECURITY_SCANNER_ENABLED=true` and configure `SKILLHUB_SECURITY_SCANNER_URL` for K8s split deployments.
- [ ] **Restrict database access**: Bind PostgreSQL and Redis to `127.0.0.1` or internal network only (`POSTGRES_BIND_ADDRESS`, `REDIS_BIND_ADDRESS`).
- [ ] **Rotate secrets**: Do not commit `.env` files with real credentials. Use your deployment platform's secret management.
- [ ] **Set public URL**: Configure `SKILLHUB_PUBLIC_BASE_URL` to your actual domain so OAuth callbacks and CLI URLs work correctly.
- [ ] **Review CORS**: If the frontend and backend are on different origins, ensure CORS is configured only for trusted domains.
- [ ] **SMTP TLS**: When enabling password reset emails, verify `SPRING_MAIL_SMTP_STARTTLS_ENABLE=true` or use SSL to protect reset codes in transit.
- [ ] **Scanner mode**: Use `SKILLHUB_SECURITY_SCANNER_MODE=upload` for split deployments (Kubernetes). Use `local` only when the backend and scanner share a filesystem.

## Dependency Security

### Backend (Java / Maven)

Dependencies are managed through the Maven dependency tree. We recommend:

- Running `mvn dependency-check:check` (OWASP Dependency-Check) periodically.
- Reviewing transitive dependency versions when updating `pom.xml`.
- Applying security patches for Spring Boot, PostgreSQL driver, and Jackson promptly.

### Frontend (Node.js / pnpm)

- Run `pnpm audit` regularly to check for known vulnerabilities in npm packages.
- Review and update `pnpm-lock.yaml` when adding or upgrading dependencies.

### CLI (Node.js / Bun)

- The SkillHub CLI (`cli/`) follows the same `pnpm audit` / `bun audit` practices.

### Container Images

Published images are built by GitHub Actions and pushed to GHCR (`ghcr.io/iflytek/skillhub-*`). Base images are pinned to specific versions (e.g., `postgres:16-alpine`, `redis:7-alpine`). Rebuild and redeploy when base image security updates are published.

## Vulnerability Handling Process

1. **Report received** → Acknowledged within 5 business days.
2. **Triage** → Maintainers assess severity, affected versions, and attack surface.
3. **Fix development** → A private branch is created; the reporter may be invited to validate the fix.
4. **Advisory publication** → A GitHub Security Advisory is published with CVE assignment (if applicable).
5. **Release** → The fix is included in the next stable release and the advisory is updated with the fix version.
6. **Disclosure** → Full details are disclosed after the fix has been available for at least 30 days, or sooner if the reporter agrees.

We ask that reporters:
- Do not publicly disclose the vulnerability before a fix is available.
- Allow reasonable time for remediation before disclosing to third parties.
- Make a good-faith effort to avoid privacy violations, data destruction, or service disruption.

## Secure Development Practices

- **SQL injection**: All database queries use JPA parameterized bindings. New code must not use string concatenation for query construction.
- **Path traversal**: All file path operations must go through `SkillPackagePolicy.normalizeEntryPath()` or equivalent `resolve().normalize() + startsWith()` checks.
- **XSS**: The frontend uses React (auto-escaping by default) and a restrictive CSP header. Avoid `dangerouslySetInnerHTML` unless the content is sanitized.
- **Secrets in code**: Never commit API keys, passwords, or tokens. Use environment variables or secret management. The `.env.release.example` file contains placeholder values only.
- **Security scanner integration**: When developing new skill-processing features, consider how they interact with the security scanning pipeline (`SCANNING` → `PENDING_REVIEW` / `SCAN_FAILED`).

## Contact

- **Security reports**: [GitHub Security Advisories](https://github.com/iflytek/skillhub/security/advisories/new)
- **General questions**: [GitHub Discussions](https://github.com/iflytek/skillhub/discussions)
- **Bug reports** (non-security): [GitHub Issues](https://github.com/iflytek/skillhub/issues)
